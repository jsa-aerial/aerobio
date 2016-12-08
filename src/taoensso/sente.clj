(ns taoensso.sente
  "Channel sockets. Otherwise known as The Shiz.

      Protocol  | client>server | client>server ?+ ack/reply | server>user push
    * WebSockets:       ✓              [1]                           ✓
    * Ajax:            [2]              ✓                           [3]

    [1] Emulate with cb-uuid wrapping.
    [2] Emulate with dummy-cb wrapping.
    [3] Emulate with long-polling.

  Abbreviations:
    * chsk   - Channel socket. Sente's own pseudo \"socket\".
    * net-ch - Network channel. Underlying web server's channel. Must implement
               Sente's async net channel interface.
    * uid    - User-id. An application-level user identifier used for async push.
               May have semantic meaning (e.g. username, email address), or not
               (e.g. client/random id) - app's discretion.
    * cb     - Callback.
    * tout   - Timeout.
    * ws     - WebSocket/s.
    * pstr   - Packed string. Arbitrary Clojure data serialized as a string (e.g.
               edn) for client<->server comms.

  Special messages:
    * Callback wrapping: [<clj> <?cb-uuid>] for [1],[2].
    * Callback replies: :chsk/closed, :chsk/timeout, :chsk/error.
    * Client-side events:
        [:chsk/handshake [<?uid> <?csrf-token> <?handshake-data>]],
        [:chsk/state <new-state>],
        [:chsk/recv <[buffered-evs]>] ; server>user push

    * Server-side events:
        [:chsk/ws-ping],
        [:chsk/bad-package <packed-str>],
        [:chsk/bad-event   <chsk-event>],
        [:chsk/uidport-open],
        [:chsk/uidport-close].

  Notable implementation details:
    * core.async is used liberally where brute-force core.async allows for
      significant implementation simplifications. We lean on core.async's strong
      efficiency here.
    * For WebSocket fallback we use long-polling rather than HTTP 1.1 streaming
      (chunked transfer encoding). Http-kit _does_ support chunked transfer
      encoding but a small minority of browsers &/or proxies do not. Instead of
      implementing all 3 modes (WebSockets, streaming, long-polling) - it seemed
      reasonable to focus on the two extremes (performance + compatibility). In
      any case client support for WebSockets is growing rapidly so fallback
      modes will become increasingly irrelevant while the extra simplicity will
      continue to pay dividends.

  General-use notes:
    * Single HTTP req+session persists over entire chsk session but cannot
      modify sessions! Use standard a/sync HTTP Ring req/resp for logins, etc.
    * Easy to wrap standard HTTP Ring resps for transport over chsks. Prefer
      this approach to modifying handlers (better portability)."

  {:author "Peter Taoussanis"}

  (:require
   [clojure.string     :as str]
   [clojure.core.async :as async
    :refer [<! <!! >! >!! put! chan
            go go-loop]]

   [taoensso.encore :as enc
    :refer [have? have have-in
            swap-in! reset-in! swapped]]

   [taoensso.timbre :as timbre
    :refer [tracef debugf infof warnf errorf]]
   [taoensso.sente.interfaces :as interfaces]

   [aerial.msgpacket.core :as packet :refer :all]
   [aerial.msgpacket.sente :refer [senteEdnMsg]]
   ))

;;;; Encore version check


(let [min-encore-version 1.21] ; v1.21+ required for *log-level*
  (if-let [assert! (ns-resolve 'taoensso.encore 'assert-min-encore-version)]
    (assert! min-encore-version)
    (throw
      (ex-info
        (format
          "Insufficient com.taoensso/encore version (< %s). You may have a Leiningen dependency conflict (see http://goo.gl/qBbLvC for solution)."
          min-encore-version)
        {:min-version min-encore-version}))))

;;;; Logging

(defn set-logging-level! [level]
  (timbre/set-level! level))


;;;; Server API

(declare ^:private send-buffered-evs>ws-clients!
         ^:private send-buffered-evs>ajax-clients!)


(defn make-channel-socket!
  "Takes a web server adapter[1] and returns a map with keys:
    :ch-recv ; core.async channel to receive `event-msg`s (internal or from clients).
    :send-fn ; (fn [user-id ev] for server>user push.
    :ajax-post-fn                ; (fn [ring-req]) for Ring CSRF-POST + chsk URL.
    :ajax-get-or-ws-handshake-fn ; (fn [ring-req]) for Ring GET + chsk URL.
    :connected-uids ; Watchable, read-only (atom {:ws #{_} :ajax #{_} :any #{_}}).

  Common options:
    :user-id-fn        ; (fn [ring-req]) -> unique user-id for server>user push.
    :csrf-token-fn     ; (fn [ring-req]) -> CSRF token for Ajax POSTs.
    :handshake-data-fn ; (fn [ring-req]) -> arb user data to append to handshake evs.
    :send-buf-ms-ajax  ; [2]
    :send-buf-ms-ws    ; [2]
    :msgpacket         ; senteEdnMsg (default), or msgPacket implementation.

  [1] e.g. `taoensso.sente.server-adapters.http-kit/http-kit-adapter` or
           `taoensso.sente.server-adapters.immutant/immutant-adapter`.
      You must have the necessary web-server dependency in your project.clj and
      the necessary entry in your namespace's `ns` form.

  [2] Optimization to allow transparent batching of rapidly-triggered
      server>user pushes. This is esp. important for Ajax clients which use a
      (slow) reconnecting poller. Actual event dispatch may occur <= given ms
      after send call (larger values => larger batch windows)."

  [web-server-adapter ; Actually a net-ch-adapter, but that may be confusing
   & [{:keys [recv-buf-or-n send-buf-ms-ajax send-buf-ms-ws
              user-id-fn csrf-token-fn handshake-data-fn msgpacket]
       :or   {recv-buf-or-n (async/sliding-buffer 1000)
              send-buf-ms-ajax 100
              send-buf-ms-ws   30
              user-id-fn    (fn [ring-req] (get-in ring-req [:session :uid]))
              csrf-token-fn (fn [ring-req]
                              (or (get-in ring-req
                                          [:session :csrf-token])
                                  (get-in ring-req
                                          [:session :ring.middleware.anti-forgery/anti-forgery-token])
                                  (get-in ring-req
                                          [:session "__anti-forgery-token"])))
              handshake-data-fn (fn [ring-req] nil)
              msgpacket senteEdnMsg}}]]

  {:pre [(have? enc/pos-int? send-buf-ms-ajax send-buf-ms-ws)
         (have? #(satisfies? interfaces/IAsyncNetworkChannelAdapter %)
           web-server-adapter)]}

  (let [std-events (packet/std-events msgpacket)
        close-chan (std-events :close-chan)
        open-conn  (std-events :open-conn)
        close-conn (std-events :close-conn)

        ch-recv (chan recv-buf-or-n)
        conns_  (atom {:ws   {} ; {<uid> {<client-id> <net-ch>}}
                       :ajax {} ; {<uid> {<client-id> [<?net-ch> <udt-last-connected>]}}
                       })
        connected-uids_ (atom {:ws #{} :ajax #{} :any #{}})
        send-buffers_   (atom {:ws  {} :ajax  {}}) ; {<uid> [<buffered-evs> <#{ev-uuids}>]}

        connect-uid!
        (fn [type uid] {:pre [(have? uid)]}
          (let [newly-connected?
                (swap-in! connected-uids_ []
                  (fn [{:keys [ws ajax any] :as old-m}]
                    (let [new-m
                          (case type
                            :ws   {:ws (conj ws uid) :ajax ajax            :any (conj any uid)}
                            :ajax {:ws ws            :ajax (conj ajax uid) :any (conj any uid)})]
                      (swapped new-m
                        (let [old-any (:any old-m)
                              new-any (:any new-m)]
                          (when (and (not (contains? old-any uid))
                                          (contains? new-any uid))
                            :newly-connected))))))]
            newly-connected?))

        upd-connected-uid! ; Useful for atomic disconnects
        (fn [uid] {:pre [(have? uid)]}
          (let [newly-disconnected?
                (swap-in! connected-uids_ []
                  (fn [{:keys [ws ajax any] :as old-m}]
                    (let [conns' @conns_
                          any-ws-clients?   (contains? (:ws   conns') uid)
                          any-ajax-clients? (contains? (:ajax conns') uid)
                          any-clients?      (or any-ws-clients?
                                                any-ajax-clients?)
                          new-m
                          {:ws   (if any-ws-clients?   (conj ws   uid) (disj ws   uid))
                           :ajax (if any-ajax-clients? (conj ajax uid) (disj ajax uid))
                           :any  (if any-clients?      (conj any  uid) (disj any  uid))}]
                      (swapped new-m
                        (let [old-any (:any old-m)
                              new-any (:any new-m)]
                          (when (and      (contains? old-any uid)
                                     (not (contains? new-any uid)))
                            :newly-disconnected))))))]
            newly-disconnected?))

        send-fn ; server>user (by uid) push
        (fn [user-id ev & [{:as opts :keys [flush? client-id]}]]
          (let [uid     (if (= user-id :sente/all-users-without-uid) ::nil-uid user-id)
                _       (tracef "Chsk send: (->uid %s) %s" uid ev)
                _       (assert uid
                          (str "Support for sending to `nil` user-ids has been REMOVED. "
                               "Please send to `:sente/all-users-without-uid` instead."))
                _       (packet/assert-event msgpacket ev)
                ev-uuid (or client-id (enc/uuid-str))

                flush-buffer!
                (fn [type]
                  (when-let
                      [pulled
                       (swap-in! send-buffers_ [type]
                         (fn [m]
                           ;; Don't actually flush unless the event buffered
                           ;; with _this_ send call is still buffered (awaiting
                           ;; flush). This means that we'll have many (go
                           ;; block) buffer flush calls that'll noop. They're
                           ;;  cheap, and this approach is preferable to
                           ;; alternatives like flush workers.
                           (let [[_ ev-uuids] (get m uid)]
                             (if (contains? ev-uuids ev-uuid)
                               (swapped (dissoc m uid)
                                        (get    m uid))
                               (swapped m nil)))))]
                    (let [[buffered-evs ev-uuids] pulled]
                      (have? vector? buffered-evs)
                      (have? set?    ev-uuids)

                      (let [[buffered-msgs combined-meta]
                            (packet/enclose msgpacket buffered-evs)]
                        (tracef "buffered-msgs: %s (with meta %s)"
                          buffered-msgs combined-meta)
                        (case type
                          :ws   (send-buffered-evs>ws-clients!   conns_
                                  uid buffered-msgs client-id)
                          :ajax (send-buffered-evs>ajax-clients! conns_
                                  uid buffered-msgs client-id))))))]

            (if (= ev close-chan) ; Currently undocumented
              (do
                (debugf "Chsk closing (client may reconnect): %s" uid)
                (when flush?
                  (doseq [type [:ws :ajax]]
                    (flush-buffer! type)))

                (doseq [net-ch (vals (get-in @conns_ [:ws uid]))]
                  (interfaces/close! net-ch))

                (doseq [[?net-ch _] (vals (get-in @conns_ [:ajax uid]))]
                  (when-let [net-ch ?net-ch]
                    (interfaces/close! net-ch))))

              (do
                ;; Buffer event
                (doseq [type [:ws :ajax]]
                  (swap-in! send-buffers_ [type uid]
                    (fn [?v]
                      (if-not ?v
                        [ev #{ev-uuid}]
                        (let [[buffered-evs ev-uuids] ?v]
                          [(conj buffered-evs ev)
                           (conj ev-uuids     ev-uuid)])))))

                ;;; Flush event buffers after relevant timeouts:
                ;; * May actually flush earlier due to another timeout.
                ;; * We send to _all_ of a uid's connections.
                ;; * Broadcasting is possible but I'd suggest doing it rarely, and
                ;;   only to users we know/expect are actually online.
                (go (when-not flush? (<! (async/timeout send-buf-ms-ws)))
                    (flush-buffer! :ws))
                (go (when-not flush? (<! (async/timeout send-buf-ms-ajax)))
                    (flush-buffer! :ajax)))))

          ;; Server-side send is async so nothing useful to return (currently
          ;; undefined):
          nil)

        ev-msg-const {:ch-recv        ch-recv
                      :send-fn        send-fn
                      :connected-uids connected-uids_}]

    {:ch-recv        ch-recv
     :send-fn        send-fn
     :connected-uids connected-uids_

     :ajax-post-fn ; Does not participate in `conns_` (has specific req->resp)
     (fn [ring-req]
       (interfaces/ring-req->net-ch-resp web-server-adapter ring-req
         {:on-open
          (fn [net-ch]
            (packet/connect-request
             msgpacket
             {:ring-req ring-req
              :type :ajax
              :ev-msg-const ev-msg-const
              :http-send-fn (partial interfaces/send! net-ch)}))}))

     :ajax-get-or-ws-handshake-fn ; Ajax handshake/poll, or WebSocket handshake
     (fn [ring-req]
       (let [con-packet
             (packet/connect-request
                  msgpacket
                  {:ring-req ring-req
                   :type :ws
                   :ev-msg-const ev-msg-const
                   :http-send-fn interfaces/send!
                   :csrf-token-fn csrf-token-fn
                   :handshake-data-fn handshake-data-fn
                   :user-id-fn user-id-fn
                   })
             client-id (con-packet :client-id)
             uid (con-packet :uid)
             websocket? (con-packet :websocket?)
             handshake! (con-packet :handshake!)
             receive-event-msg! (con-packet :receive-event-msg!)]

         (interfaces/ring-req->net-ch-resp web-server-adapter ring-req
           {:on-open
            (fn [net-ch]
              (if websocket?
                (do ; WebSocket handshake
                  (tracef "New WebSocket channel: %s (%s)"
                          uid (str net-ch)) ; _Must_ call `str` on net-ch
                  (reset-in! conns_ [:ws uid client-id] net-ch)
                  (let [con-uid (connect-uid! :ws uid)]
                    (when con-uid
                      (tracef "Connect-uid call: %s" con-uid)
                      (receive-event-msg! open-conn))
                    (handshake! net-ch)))

                ;; Ajax handshake/poll connection:
                (let [params (get ring-req :params)
                      initial-conn-from-client?
                      (swap-in! conns_ [:ajax uid client-id]
                                (fn [?v] (swapped [net-ch (enc/now-udt)] (nil? ?v))))

                      handshake? (or initial-conn-from-client?
                                     (:handshake? params))]

                  (when (connect-uid! :ajax uid)
                    (receive-event-msg! open-conn))

                  ;; Client will immediately repoll:
                  (when handshake? (handshake! net-ch)))))

            :on-msg ; Only for WebSockets
            (fn [net-ch line-data]
              (tracef "Line Data: %s" line-data)
              (let [msg (packet/open msgpacket net-ch line-data)]
                (tracef "#### packet MSG: %s" msg)
                (receive-event-msg! msg)))


            :on-close ; We rely on `on-close` to trigger for _every_ conn!
            (fn [net-ch status]
              ;; `status` is currently unused; its form varies depending on
              ;; the underlying web server

              (if websocket?
                (do ; WebSocket close
                  (swap-in!
                   conns_ [:ws uid]
                   (fn [?m]
                     (let [new-m (dissoc ?m client-id)]
                       (if (empty? new-m) :swap/dissoc new-m))))

                  ;; (when (upd-connected-uid! uid)
                  ;;   (receive-event-msg! close-conn))

                  (go
                   ;; Allow some time for possible reconnects (sole window
                   ;; refresh, etc.):
                   (<! (async/timeout 5000))

                   ;; Note different (simpler) semantics here than Ajax
                   ;; case since we don't have/want a `udt-disconnected` value.
                   ;; Ajax semantics: 'no reconnect since disconnect+5s'.
                   ;; WS semantics: 'still disconnected after disconnect+5s'.
                   ;;
                   (when (upd-connected-uid! uid)
                     (receive-event-msg! close-conn))))

                (do ; Ajax close
                  (swap-in!
                   conns_ [uid :ajax client-id]
                   (fn [[net-ch udt-last-connected]] [nil udt-last-connected]))

                  (let [udt-disconnected (enc/now-udt)]
                    (go
                     ;; Allow some time for possible poller reconnects:
                     (<! (async/timeout 5000))
                     (let [disconnected? (swap-in!
                                          conns_ [:ajax uid]
                                          (fn [?m]
                                            (let [[_ ?udt-last-connected] (get ?m client-id)
                                                  disconnected?
                                                  (and ?udt-last-connected ; Not yet gc'd
                                                       (>= udt-disconnected
                                                           ?udt-last-connected))]
                                              (if-not disconnected?
                                                (swapped ?m (not :disconnected))
                                                (let [new-m (dissoc ?m client-id)]
                                                  (swapped
                                                   (if (empty? new-m) :swap/dissoc new-m)
                                                   :disconnected))))))]
                       (when disconnected?
                         (when (upd-connected-uid! uid)
                           (receive-event-msg! close-conn)))))))))})))}))


(defn- send-buffered-evs>ws-clients!
  "Actually pushes buffered events (as packed-str) to all uid's WebSocket conns."
  [conns_ uid buffered-msgs client-id]
  (tracef "send-buffered-evs>ws-clients!: %s" buffered-msgs)
  (let [id-chan-map (get-in @conns_ [:ws uid])]
    (tracef ">>> id-chan-map: %s" (str id-chan-map))
    (if client-id
      (interfaces/send! (id-chan-map client-id) buffered-msgs)
      (doseq [net-ch (vals id-chan-map)]
        (interfaces/send! net-ch buffered-msgs)))))



(defn- send-buffered-evs>ajax-clients!
  "Actually pushes buffered events (as packed-str) to all uid's Ajax conns.
  Allows some time for possible Ajax poller reconnects."
  [conns_ uid buffered-evs-pstr & [{:keys [nmax-attempts ms-base ms-rand client-id]
                                    ;; <= 7 attempts at ~135ms ea = 945ms
                                    :or   {nmax-attempts 7
                                           ms-base       90
                                           ms-rand       90}}]]
  (comment (* 7 (+ 90 (/ 90 2.0))))
  (let [;; All connected/possibly-reconnecting client uuids:
        client-ids-unsatisfied (keys (get-in @conns_ [:ajax uid]))
        client-ids-unsatisfied (if (and client-id
                                        (contains? client-ids-unsatisfied
                                                   client-id))
                                 #{client-id}
                                 client-ids-unsatisfied)]
    (when-not (empty? client-ids-unsatisfied)
      ;; (tracef "client-ids-unsatisfied: %s" client-ids-unsatisfied)
      (go-loop [n 0 client-ids-satisfied #{}]
        (let [?pulled ; nil or {<client-id> [<?net-ch> <udt-last-connected>]}
              (swap-in! conns_ [:ajax uid]
                (fn [m] ; {<client-id> [<?net-ch> <udt-last-connected>]}
                  (let [ks-to-pull (remove client-ids-satisfied (keys m))]
                    ;; (tracef "ks-to-pull: %s" ks-to-pull)
                    (if (empty? ks-to-pull)
                      (swapped m nil)
                      (swapped
                        (reduce
                          (fn [m k]
                            (let [[?net-ch udt-last-connected] (get m k)]
                              (assoc m k [nil udt-last-connected])))
                          m ks-to-pull)
                        (select-keys m ks-to-pull))))))]
          (have? [:or nil? map?] ?pulled)
          (let [?newly-satisfied
                (when ?pulled
                  (reduce-kv
                   (fn [s client-id [?net-ch _]]
                     (if (or (nil? ?net-ch)
                             ;; net-ch may have closed already (`send!` will noop):
                             (not (interfaces/send! ?net-ch buffered-evs-pstr
                                    :close-after-send)))
                       s
                       (conj s client-id))) #{} ?pulled))
                now-satisfied (into client-ids-satisfied ?newly-satisfied)]
            ;; (tracef "now-satisfied: %s" now-satisfied)
            (when (and (< n nmax-attempts)
                       (some (complement now-satisfied) client-ids-unsatisfied))
              ;; Allow some time for possible poller reconnects:
              (<! (async/timeout (+ ms-base (rand-int ms-rand))))
              (recur (inc n) now-satisfied))))))))



;;;; Router wrapper

(defn start-chsk-router!
  "Creates a go-loop to call `(event-msg-handler <event-msg>)` and returns a
  `(fn stop! [])`. Catches & logs errors. Advanced users may choose to instead
  write their own loop against `ch-recv`."
  [ch-recv msgpacket event-msg-handler & [{:as opts :keys [trace-evs?]}]]
  (let [ch-ctrl (chan)]
    (go-loop []
      (when-not
        (enc/kw-identical? ::stop
          (try
            (let [[v p] (async/alts! [ch-recv ch-ctrl])]
              (if (enc/kw-identical? p ch-ctrl) ::stop
                  (let [{:as event-msg :keys [event msg]} v]
                  (try
                    (when trace-evs?
                      (tracef "Pre-handler event: %s" (or event msg)))
                    (if-not (packet/event-msg? msgpacket event-msg)
                      ;; Shouldn't be possible here, but we're being cautious:
                      (errorf "Bad event: %s" (or event msg)) ; Log 'n drop
                      (event-msg-handler event-msg))
                    nil
                    (catch
                            Throwable
                                      ; :default ; Temp workaround for [1]
                      t
                      (errorf       t
                        "Chsk router handling error: %s" (or msg event)))))))
            (catch
                    Throwable
                              ; :default [1] Temp workaround for [1]
              t
              (errorf       t
                "Chsk router channel error!"))))

        ;; TODO [1]
        ;; @shaharz reported (https://github.com/ptaoussanis/sente/issues/97)
        ;; that current releases of core.async have trouble with :default error
        ;; catching, Ref. http://goo.gl/QFBvfO.
        ;; The issue's been fixed but we're waiting for a new core.async
        ;; release.

        (recur)))
    (fn stop! [] (async/close! ch-ctrl))))


;;;; Deprecated
(defn start-chsk-router-loop!
  "DEPRECATED: Please use `start-chsk-router!` instead."
  [event-msg-handler ch-recv]
  (start-chsk-router! ch-recv
    ;; Old handler form: (fn [ev-msg ch-recv])
    (fn [ev-msg] (event-msg-handler ev-msg (:ch-recv ev-msg)))))
