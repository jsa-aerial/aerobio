(ns aerial.msgpacket.binaryjs
  (:require
   [clojure.string     :as str]
   [clojure.core.async :as async]

   [taoensso.timbre
    :as timbre
    :refer (debugf tracef warnf errorf)]
   [taoensso.sente.interfaces
    :as interfaces]

   [binpack.binpacker :as binary-pack]
   [aerial.msgpacket.core :as msgpkt
    :refer :all]
   ))


(defn- validate-event
  "Check if x is a valid binaryjs event.  Valid events are of the
   form: [type, payload, bonus ], where

   Reserved
   [ 0  , X , X ]

   New stream
   [ 1  , Meta , new streamId ]

   Data
   [ 2  , Data , streamId ]

   Pause
   [ 3  , null , streamId ]

   Resume
   [ 4  , null , streamId ]

   End
   [ 5  , null , streamId ]

   Close
   [ 6  , null , streamId ]

   Error
   [ 7  , ErrorInfo , streamId ]
  "
  [x]
  (or (and (map? x)
           (x :id)
           (validate-event (x :msg)))
      (and (vector? x)
           (<= 2 (count x) 3)
           (keyword? (first x)))))

(defn- event-?
  "Valid msg event protocol form?"
  [x]
  (validate-event x))

(defn- streamid? [x] (integer? x))
(defn- event-msg-?
  "Returns true if x is a valid binaryjs msg protocol value"
  [x]
  (and (event-? x)
       (let [[id payload meta] (if (map? x) (x :msg) x)]
         (and
          (or (nil? payload)
              (= (type payload) (Class/forName "[B"))
              (string? payload)
              (map? payload)
              (vector? payload))
          (or (nil? meta)
              (streamid? meta))))))

(defn- as-event
  "Force event form - x if valid otherwise explicit bad event:
  [:bad-event x nil]
  "
  [x]
  (if (event-? x) x [:bad-event x nil]))

(defn- to-bjsmsg [item]
  (cond
   (= item :open-conn) [:connection nil nil]
   (= item :close-conn) [:close-conn nil nil]
   (event-? item) (if (map? item) (item :msg) item)
   :else [:badmsg nil nil]))

(defn- get-event [m] (if (map? m) (m :id) (first m)))


(def strmev<->kwev
     {:new 1, 1 :new
      :data 2, 2 :data
      :pause 3, 3 :pause
      :resume 4, 4 :resume
      :end 5, 5 :end
      :close 6, 6 :close
      :error 7, 7 :error})

(defn- pack
  ""
  ([packer data]
     (assert (or (vector? data) (map? data)))
     (let [[id payload streamid] (if (map? data) (data :msg) data)
           msg [(strmev<->kwev id) payload streamid]]
       (interfaces/pack packer msg)))
  ([packer ev streamid]
     (pack packer [ev nil streamid]))
  ([packer ev payload streamid]
     (pack packer [ev payload streamid])))

(defn- unpack
  ""
  [packer line-data]
  (let [bjsmsg (into [] (interfaces/unpack packer line-data))
        [bjsev payload streamid] bjsmsg
        kwev (strmev<->kwev bjsev)
        msg [kwev payload streamid]
        _ (assert (event-msg-? msg) (str "BAD binaryjs msg: " bjsmsg))
        cljmsg (assoc {} :msg msg :id kwev)]
    cljmsg))

(defn- msg->recv-chan!
  "All server-side `event-msg`s go through this."
  [recv-chan msg]
  (let [msg (as-event msg)]
    (if-not (validate-event msg)
      (warnf "MSG->RECV-CHAN!: Bad ev-msg: %s" msg)
      (async/put! recv-chan msg))))




(defn- ajax-connect [packer req]
  (assert false "Attempt to use Ajax with BinaryJS!!!"))


(defn- next-streamid [clients client-id]
  (let [streamid (get-in @clients [client-id :cur-streamid])]
    (swap! clients assoc-in [client-id :cur-streamid] (+ 2 streamid))
    streamid))

(defn new-stream [clients client-id & [streamid meta]]
  (let [srvstrm   (nil? streamid) ; is this a server side proxy client stream?
        streamid  (or streamid (next-streamid clients client-id))
        socket    (get-in @clients [client-id :socket])
        http-send (get-in @clients [client-id :http-send])
        packer    (get-in @clients [client-id :packer])
        cchan     (get-in @clients [client-id :cchan])
        schan     (async/chan 100)
        getstream (fn[id] (@(get-in @clients [client-id :streams]) id))
        setstream (fn[id S]  (swap! (get-in @clients [client-id :streams]) assoc id S))
        setfields (fn[id & kvs](setstream id (apply assoc (getstream id) kvs)))

        write (fn write
                ([data-msg]
                   (http-send socket (pack packer data-msg) false))
                ([msgid payload]
                   (let [S (getstream streamid)]
                     (if (S :writable)
                       (write [msgid payload streamid])
                       (msg->recv-chan!
                        cchan
                        {:id :error
                         :msg [:error (format "Stream not writable: %s -> %s"
                                              [msgid payload] S) nil]
                         :client (@clients client-id)
                         :stream (getstream streamid)})))))

        onPause (fn[] (setfields streamid :paused true))
        onResume (fn[] (setfields streamid :paused false))

        onEnd (fn[& [viaend]]
                (let [ended ((getstream streamid) :ended)]
                  (when (not ended)
                    (setfields streamid :ended true :readable false)
                    (when-not viaend #_(emit :end)))))

        onClose (fn[]
                   (let [closed ((getstream streamid) :closed)]
                     (when (not closed)
                       (setfields
                        streamid :readable false :writable false :closed true)
                       (swap! (get-in @clients [client-id :streams])
                              dissoc streamid)
                       (async/close! schan))))

        pause (fn[] (onPause) (write :pause nil))
        resume (fn[] (onResume) (write :resume nil))
        end (fn[] (onEnd true) (write :end nil))
        destroy (fn[] (onClose) (write :close nil))

        stream {:id streamid
                :socket socket, :schan schan
                :writable true, :readable true, :paused false
                :closed false, :ended false
                :setfields setfields
                :pause pause, :onPause onPause
                :resume resume, :onResume onResume
                :onClose onClose, :onEnd onEnd
                :end end, :onData (fn[_]),
                :onError (fn[_]), :write write}
        stream (assoc stream :on
                 (fn[ev f]
                   (setstream
                    streamid
                    (assoc (getstream streamid)
                      ev (if (some #{ev} [:onError :onData])
                           f
                           (fn[](stream ev)(f)))))))]

    (setstream streamid stream)
    (when srvstrm (write [:new meta streamid]))
    stream))


(let [next (atom 0)]
  (defn next-clientid []
    (swap! next inc)))

(defn new-client [id recv-chan packer http-send]
  ;; Clients can have any number of streams; by binaryjs convention,
  ;; server side 'clients' have odd integer ids
  {:id id, :cchan recv-chan
   :packer packer, :http-send http-send
   :streams (atom {}), :cur-streamid 1})


(defn process-bjs-msg
  "Middleware for binaryjs messages. The only messages generated
  explicitly by binaryjs are those for 'streams'.  Streams are virtual
  entities layered on clients to enable clients to multiplex data flow
  across multiple 'topics'. So, the basic server connection entities
  are 'clients' (connections at the websocket level) and within such a
  client connection you can have many streams of information
  flowing. So, streams have their own 'subprotocol'.  See valid-event
  for a description of this. Most of this subprotocol is reflected
  directly to the application level, but some bookkeeping is needed to
  keep track of streams and enable the driving of them from the
  application level.  This function handles this 'middleware'
  functionality.

  NOTE: terminology across msg protocols here is a pain since everyone
  has different terms for the same thing.  For example, a 'client' in
  bjs is equivalent to a 'user' in sente. A client in sente plays
  something of an analogous role as a stream in bjs, execept there is
  no notion of, nor support for, distinct conversations.
  "
  [msgpacket msgmap]
  (let [[evid payload streamid :as bjsmsg] (to-bjsmsg msgpacket)]
    (if (= evid :connection)
      :noop ; Handle in connect! (sente 'handshake') function
      (let [clients (msgmap :clients)
            client-id (msgmap :client-id)
            cchan (get-in @clients [client-id :cchan])]

        (case evid
          :connect ; Basically server side open
          (msg->recv-chan!
           cchan {:id :connection :msg [:open nil]
                  :client (@clients client-id) :msgmap msgmap})

          :close-conn ; RMT client closed
          (let [client (@clients client-id)]
            (swap! clients dissoc client-id)
            (msg->recv-chan!
             cchan {:id :close :msg [:close nil]
                    :client client :msgmap msgmap}))

          :new ; RMT client created new stream
          (let [meta payload ; this case, payload is 'meta' info
                nbs (new-stream clients client-id streamid)]
            (swap! (get-in @clients [client-id :streams])
                   assoc (nbs :id) nbs)
            (msg->recv-chan!
             cchan {:id :stream :msg [:stream [nbs meta]]
                    :client (@clients client-id) :msgmap msgmap}))

          (:data :error :pause :resume :end :close)
          (let [stream (@(get-in @clients [client-id :streams]) streamid)]
            (if-not stream
              (msg->recv-chan!
               cchan
               {:id :error
                :msg [:error (format "Received unknown stream msg: %s" bjsmsg)]
                :client (@clients client-id)})
              (case evid
                :data (-> stream :onData (#(% payload)))
                :error (-> stream :onError (#(% payload)))
                :pause (-> stream :onPause (#(%)))
                :resume (-> stream :onResume (#(%)))
                :end (-> stream :onEnd (#(%)))
                :close (-> stream :onClose (#(%)))))))
        ))))


(defn- ws-connect [packer req clients client-id]
  (let [ring-req     (:ring-req req)
        ev-msg-const (:ev-msg-const req)
        recv-chan    (:ch-recv ev-msg-const)
        http-send!   (:http-send-fn req)

        csrf-token-fn     (:csrf-token-fn req)
        user-id-fn        (:user-id-fn req)
        handshake-data-fn (:handshake-data-fn req)

        csrf-token (csrf-token-fn ring-req)
        params     (get ring-req :params)
        uid        (or (user-id-fn
                        ;; Allow uid to depend on client-id
                        ;; (keep these private if being used for uids!!)
                        (assoc ring-req :client-id client-id))
                       ::nil-uid)
        websocket? (:websocket? ring-req)

        _ (swap! clients assoc client-id
                 (new-client client-id recv-chan packer http-send!))

        receive-event-msg! ; Partial
        (fn [msgpacket]
          (tracef "!!!! receive-event-msg! %s" msgpacket)
          (process-bjs-msg
           msgpacket
           (merge ev-msg-const
              {:client-id client-id
               :clients clients
               :ring-req  ring-req})))

        connect!
        (fn [net-ch]
          (tracef "Connect: %s" (str net-ch))
          (swap! clients assoc-in [client-id :socket] net-ch)
          (receive-event-msg! [:connect net-ch nil]))


        msg (merge ev-msg-const
                   {:ring-req  ring-req
                    :client-id client-id
                    :csrf-token csrf-token
                    :uid uid
                    :websocket? websocket?
                    :receive-event-msg! receive-event-msg!
                    :handshake! connect!})]
    msg))


(defn- enclose- [packer data]
  (let [[msg] data] ; protocol enclose data is & "rest" arg
    (if-not (validate-event msg)
      (warnf "ENCLOSE: Bad ev-msg: %s" msg)
      (pack packer msg))))


(def dbg-data (atom nil))

(defrecord binaryjs
  [packer clients]

  msgPacket

  (std-events [_]
    {:open-conn  :open-conn
     :close-conn :close-conn
     :close-chan :close-chan})

  (event? [_ x] (event-? x))

  (event-msg? [_ x] (event-msg-? x))

  ;; Assert that x is a true event or throw exception
  (assert-event [_ x]
    (when (not (validate-event x))
      (let [err-fmt
            (str x "Event should be of form "
                 "`[int-event-id array-buf-payload meta]`")]
        (throw (ex-info err-fmt {:malformed-event x})))))

  (event [_ x] (get-event x))

  (connect-request [_ req]
    (if (= (:type req) :ajax)
      (ajax-connect packer req)
      (ws-connect packer req clients (next-clientid))))

  (enclose_ [_ data] (enclose- packer data))

  (enclose-send_ [_ chan data]
    (let [msg (enclose- packer data)]
      (interfaces/send! chan msg)))

  (open_ [_ data]
    (tracef "OPEN data: %s" data)
    (let [[net-ch line-data] data]
      #_(swap! dbg-data (fn[_] [data line-data]))
      (unpack packer line-data)))

  (receive-open [_ chan] :noop))

(defn newBinaryJsMsg []
  (->binaryjs binary-pack/binary-packer (atom {})))
