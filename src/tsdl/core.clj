(ns tsdl.core
  (:require [tsdl.ui :as ui]))

(defn -main []
  (case (ui/login-dialog)
    :ok (ui/display-progress)
    :cancel nil))

#_(-main)
