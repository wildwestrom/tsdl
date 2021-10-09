(ns tsdl.ui
  (:require [clojure.core.async :as async :refer [go-loop]]
            [com.brunobonacci.mulog :as mu]
            [seesaw.core :as s]
            [seesaw.forms :as f]
            [seesaw.widgets.log-window :as l]
            [tsdl.downloader :as dl]
            [tsdl.logging :as logging]))

(set! *warn-on-reflection* true)

(defonce username (atom (:username @dl/cache)))
(defonce password (atom (:password @dl/cache)))

(defn log-to-window [w s]
  (let [log-window (:log-window (s/group-by-id w))]
    (l/log log-window s))
  w)

(def downloader-controls
  (s/vertical-panel
   :items [(s/action :name "Start"
                     :enabled? true
                     :handler (fn [_]
                                (dl/download-all-paintings)))
           (s/action :name "Cancel"
                      ;; TODO Change this to something a little more graceful.
                      ;; I just want it to stop downloading when I hit cancel.
                     :handler (fn [_]
                                (mu/log ::shutdown :message "Shutting down!")
                                (System/exit 1)))]))

(defn display-progress []
  (-> (s/frame
       :title "Progress"
       :minimum-size [640 :by 400]
       :on-close :exit
       :content (s/border-panel
                 :center (s/scrollable
                          (l/log-window :id :log-window
                                        :auto-scroll? true)
                          :vscroll :always)
                 :south downloader-controls))
      (s/pack!)
      (s/show!)
      ((fn [w]
         (go-loop []
           (log-to-window
            w
            (str (dissoc (async/<! logging/log-chan)
                         :mulog/trace-id :mulog/timestamp :mulog/namespace)
                 "\n"))
           (recur))))))

;; TODO Figure out why the frick things from other namespaces won't use my publisher.
#_(mu/log ::test-event :tested true)
#_(display-progress)

(defn text-span [& args]
  (let [{:keys [id field-type text col-len]} args]
    (f/span (field-type
             :id id
             :text text)
            col-len)))

(defn listen-for-changes [w]
  (let [{:keys [username-field password-field]} (s/group-by-id w)]
    (s/listen username-field :document (fn [e] (reset! username (str (s/text e)))))
    (s/listen password-field :document (fn [e] (reset! password (str (s/text e))))))
  w)

(defn login-dialog []
  (-> (let [feedback-label (s/label
                            :id :log-in-feedback
                            :text nil)]
        (s/dialog
         :title "Log-in"
         :id :login
         :options [(s/action
                    :name "Log in"
                    :handler (fn [e]
                               (if (or (empty? @username)
                                       (empty? @password))
                                 (s/text! feedback-label
                                          "One of the required fields is empty.")
                                 (if @dl/logged-in?
                                   (s/return-from-dialog e :ok)
                                   (if (dl/log-in {:username @username :password @password})
                                     (s/return-from-dialog e :ok)
                                     (s/text! feedback-label
                                              "Could not log in; Please try again."))))))
                   (s/action :name "Cancel"
                             :handler (fn [e] (s/return-from-dialog e :cancel)))]
         :content (let [col-len 4]
                    (f/forms-panel
                     "pref,4dlu,80dlu,8dlu,pref,4dlu,80dlu"
                     :default-dialog-border? true
                     :column-groups [[1 col-len]]
                     :leading-column-offset 0
                     :items [(f/title "Please log-in:")
                             (f/next-line)
                             "Username:" (text-span
                                          :id :username-field
                                          :field-type s/text
                                          :text @username
                                          :col-len col-len)
                             (f/next-line)
                             "Password:" (text-span
                                          :id :password-field
                                          :field-type s/password
                                          :text @password
                                          :col-len col-len)
                             (f/next-line)
                             (f/span feedback-label col-len)]))))
      listen-for-changes
      s/pack!
      s/show!))
