(ns aerial.msgpacket.sente
  (:require
   [clojure.string     :as str]
   [clojure.core.async :as async]

   [taoensso.encore
    :as enc
    :refer (have? have have-in
            swap-in! reset-in!
            swapped)]
   [taoensso.timbre
    :as timbre
    :refer (debugf tracef warnf errorf)]
   [taoensso.sente.interfaces
    :as interfaces]
   [taoensso.sente.packers.transit
    :as sente-transit]

   [aerial.msgpacket.core :as msgpkt
    :refer :all]
   ))

;;;; Events
;; * Clients & server both send `event`s and receive (i.e. route) `event-msg`s.
;;
;;;; Packing
;; * Client<->server payloads are arbitrary Clojure vals (cb replies or events).
;; * Payloads are packed for client<->server transit.
;; * Packing includes ->str encoding, and may incl. wrapping to carry cb info.



(defn- unpack* "pstr->clj" [packer pstr]
  (try
    (interfaces/unpack packer (have string? pstr))
    (catch       Throwable                 t
      (debugf "Bad package: %s (%s)" pstr t)
             [:chsk/bad-package pstr]
                       ; Let client rethrow on bad pstr from server
      )))

(defn- with-?meta [x ?m]
  (if (seq ?m) (with-meta x ?m) x))

(defn- pack* "clj->prefixed-pstr"
  ([packer ?packer-meta clj]
     (str "-" ; => Unwrapped (no cb metadata)
       (interfaces/pack packer (with-?meta clj ?packer-meta))))

  ([packer ?packer-meta clj ?cb-uuid]
     (let [;;; Keep wrapping as light as possible:
           ?cb-uuid    (if (= ?cb-uuid :ajax-cb) 0 ?cb-uuid)
           wrapped-clj (if ?cb-uuid [clj ?cb-uuid] [clj])]
       (str "+" ; => Wrapped (cb metadata)
         (interfaces/pack packer (with-?meta wrapped-clj ?packer-meta))))))

(defn- pack [& args]
  (let [pstr (apply pack* args)]
    (tracef "Packing: %s -> %s" args pstr)
    pstr))

(defn- unpack "prefixed-pstr->[clj ?cb-uuid]"
  [packer prefixed-pstr]
  (have? string? prefixed-pstr)
  (let [prefix   (enc/substr prefixed-pstr 0 1)
        pstr     (enc/substr prefixed-pstr 1)
        clj      (unpack* packer pstr) ; May be un/wrapped
        wrapped? (case prefix "-" false "+" true)
        [clj ?cb-uuid] (if wrapped? clj [clj nil])
        ?cb-uuid (if (= 0 ?cb-uuid) :ajax-cb ?cb-uuid)]
    (tracef "Unpacking: %s -> %s" prefixed-pstr [clj ?cb-uuid])
    [clj ?cb-uuid]))



(defn- validate-event [x]
  (cond
    (not (vector? x))        :wrong-type
    (not (#{1 2} (count x))) :wrong-length
    :else (let [[ev-id _] x]
            (cond (not (keyword? ev-id))  :wrong-id-type
                  (not (namespace ev-id)) :unnamespaced-id
                  :else nil))))

(defn- event-? [x]
  (nil? (validate-event x)))

(defn- event-msg-? [x]
  (and
   (map? x)
   (enc/keys= x #{:ch-recv :send-fn :connected-uids
                  :ring-req :client-id
                  :event :id :?data :?reply-fn})
   (let [{:keys [ch-recv send-fn connected-uids
                 ring-req client-id event ?reply-fn]} x]
     (and
      (enc/chan?       ch-recv)
      (ifn?            send-fn)
      (enc/atom?       connected-uids)
      ;;
      (map?            ring-req)
      (enc/nblank-str? client-id)
      (event-?         event)
      (or (nil? ?reply-fn)
          (ifn? ?reply-fn))))))

(defn- as-event [x]
  (if (event-? x) x [:chsk/bad-event x]))

(defn- get-event [m]
  (:event m))



(defn- put-event-msg>ch-recv!
  "All server-side `event-msg`s go through this."
  [ch-recv {:as ev-msg :keys [event ?reply-fn]}]
  (let [[ev-id ev-?data :as valid-event] (as-event event)
        ev-msg* (merge ev-msg {:event     valid-event
                               :?reply-fn ?reply-fn
                               :id        ev-id
                               :?data     ev-?data})]
    (if-not (event-msg-? ev-msg*)
      (warnf "Bad ev-msg: %s" ev-msg) ; Log 'n drop
      (async/put! ch-recv ev-msg*))))



(defn- ajax-connect [packer req]
  (let [ring-req (:ring-req req)
        ev-msg-const  (:ev-msg-const req)
        ch-recv       (:ch-recv ev-msg-const)
        http-send!    (:http-send-fn req)
        params        (get ring-req :params)
        ppstr         (get params   :ppstr)
        ;; client-id (get params   :client-id) ; Unnecessary here
        [clj has-cb?] (interfaces/unpack packer ppstr)
        reply-fn (when has-cb?
                   (fn reply-fn [resp-clj] ; Any clj form
                     (tracef "Chsk send (ajax reply): %s" resp-clj)
                     ;; true iff apparent success:
                     (http-send! (pack packer (meta resp-clj) resp-clj)
                                 :close-after-send)))
        default-reply-fn (when-not reply-fn
                           (fn[]
                             (tracef "Chsk send (ajax reply): dummy-cb-200")
                             (http-send! (pack packer nil :chsk/dummy-cb-200)
                                         :close-after-send)))
        msg (merge ev-msg-const
                   {:client-id "unnecessary-for-non-lp-POSTs"
                    :ring-req  ring-req
                    :event     clj
                    :default-reply-fn default-reply-fn
                    :?reply-fn reply-fn})]
    (put-event-msg>ch-recv! ch-recv msg)
    (when default-reply-fn
      (default-reply-fn))))


(defn- get-client-id [params]
  (get params :client-id (str "client-id-" (str (rand)))))

(defn- ws-connect [packer req]
  (let [ring-req     (:ring-req req)
        ev-msg-const (:ev-msg-const req)
        ch-recv      (:ch-recv ev-msg-const)
        http-send!   (:http-send-fn req)

        csrf-token-fn     (:csrf-token-fn req)
        user-id-fn        (:user-id-fn req)
        handshake-data-fn (:handshake-data-fn req)

        csrf-token (csrf-token-fn ring-req)
        params     (get ring-req :params)
        client-id  (get-client-id params)
        uid        (or (user-id-fn
                        ;; Allow uid to depend on client-id
                        ;; (keep these private if being used for uids!!)
                        (assoc ring-req :client-id client-id))
                       ::nil-uid)
        websocket? (:websocket? ring-req)

        receive-event-msg! ; Partial
        (fn [msgpacket]
          (let [[event & [?reply-fn]] msgpacket]
            (tracef "!!!! receive-event-msg! %s, %s" event ?reply-fn)
            (put-event-msg>ch-recv!
             ch-recv
             (merge ev-msg-const
                    {:client-id client-id
                     :ring-req  ring-req
                     :event     event
                     :?reply-fn ?reply-fn}))))
        handshake!
        (fn [net-ch]
          (tracef "Handshake!")
          (let [?handshake-data (handshake-data-fn ring-req)
                handshake-ev
                (if-not (nil? ?handshake-data) ; Micro optimization
                  [:chsk/handshake [uid csrf-token ?handshake-data]]
                  [:chsk/handshake [uid csrf-token]])]
            (http-send!
             net-ch
             (pack packer nil handshake-ev)
             (not websocket?))))

        msg (merge ev-msg-const
                   {:ring-req  ring-req
                    :client-id client-id
                    :csrf-token csrf-token
                    :uid uid
                    :websocket? websocket?
                    :receive-event-msg! receive-event-msg!
                    :handshake! handshake!})]

    (if (str/blank? client-id)
      (let [err-msg (str
                     "Client's Ring request doesn't have a client id. "
                     "Does your server have the necessary keyword Ring middleware: "
                     "(`wrap-params` & `wrap-keyword-params`)?")]
             (errorf (str err-msg \n ring-req \n))
             (throw (ex-info err-msg {:ring-req ring-req})))
      msg)))



(defn- enclose- [packer info & [?cb-uuid]]
  (let [vinfo (if (seq? info) info (vector info))
        packer-metas (mapv meta vinfo)
        meta (reduce merge {} packer-metas)
        buffered-evs-payload
        (if ?cb-uuid
          (pack packer meta info ?cb-uuid)
          (pack packer meta info))]
    [buffered-evs-payload meta]))



(defrecord sentemsg
  [packer]

  msgPacket

  (std-events [_]
    {:open-conn    [[:chsk/uidport-open]]
     :close-conn   [[:chsk/uidport-close]]
     :close-chan   [:chsk/close]
     :chan-timeout [:chsk/timeout]
     :chan-error   [:chsk/error]
     :bad-packet   [:chsk/bad-package]
     :bad-event    [:chsk/bad-event]
     })

  (event? [_ x] (event-? x))

  (event-msg? [_ x]
    (event-msg-? x))

  (assert-event [_ x]
    (when-let [?err (validate-event x)]
      (let [err-fmt
            (str
             (case ?err
                   :wrong-type   "Malformed event (wrong type)."
                   :wrong-length "Malformed event (wrong length)."
                   (:wrong-id-type :unnamespaced-id)
                   "Malformed event (`ev-id` should be a namespaced keyword)."
                   :else "Malformed event (unknown error).")
             " Event should be of `[ev-id ?ev-data]` form: %s")]
        (throw (ex-info (format err-fmt (str x)) {:malformed-event x})))))

  (event [_ m] (get-event m))

  (connect-request [_ req]
    (if (= (:type req) :ajax)
      (ajax-connect packer req)
      (ws-connect packer req)))

  (enclose_ [_ data]
    (let [[[info & [?cb-uuid]] _] data]
      (enclose- packer info ?cb-uuid)))

  (enclose-send_ [_ chan data]
    (let [msg (apply enclose- packer data)]
      (interfaces/send! chan msg)))

  (open_ [_ data]
    (tracef "OPEN data: %s" data)
    (let [[net-ch line-data] data
          [clj ?cb-uuid] (unpack packer line-data)]
      [clj ; Should be ev
       (when ?cb-uuid
         (fn reply-fn [resp-clj] ; Any clj form
           (tracef "Chsk send (ws reply): %s" resp-clj)
           ;; true iff apparent success:
           (interfaces/send!
            net-ch
            (pack packer (meta resp-clj) resp-clj ?cb-uuid))))]))

  (receive-open [_ chan]
    :noop)
  )

(def senteEdnMsg
     (->sentemsg (sente-transit/get-flexi-packer :edn)))

