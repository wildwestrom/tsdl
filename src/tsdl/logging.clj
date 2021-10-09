(ns tsdl.logging
  (:require [clojure.core.async :as async]
            [com.brunobonacci.mulog :as mu]
            [com.brunobonacci.mulog.buffer :as rb]))

(deftype AsyncPublisher [config buffer]
    com.brunobonacci.mulog.publisher.PPublisher

    (agent-buffer [_]
      buffer)

    (publish-delay [_]
      500)

    (publish [_ buffer]
        (doseq [item (map second (rb/items buffer))]
          (async/>!! (:chan config) item))
        (rb/clear buffer)))

(defn async-publisher
  [config]
  (AsyncPublisher. config (rb/agent-buffer 10000)))

(def log-chan (async/chan))

(mu/start-publisher!
 {:type :multi
  :publishers [{:type :console}
               {:type :custom
                :fqn-function "tsdl.logging/async-publisher"
                :chan log-chan
                :pretty-print true}]})
