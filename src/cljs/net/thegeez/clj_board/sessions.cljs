(ns net.thegeez.clj-board.sessions
  (:require [re-frame.core :refer [dispatch dispatch-sync] :as rf]
            [secretary.core :as secretary :refer-macros [defroute]]
            [ajax.core :as ajax]
            [goog.Uri]
            [net.thegeez.clj-board.socket :as socket]
            [net.thegeez.clj-board.url-location :as url-location]))

(def init
  {:sessions/current-user nil
   :sessions/error nil})

;; sessions
(rf/reg-event-db
 :sessions/current-user
 (fn [db [_ data]]
   (assoc db
          :sessions/current-user data
          :sessions/error nil)))

(rf/reg-event-db
 :sessions/session-error
 (fn [db [_ error]]
   (assoc db :sessions/error (:error error))))

(defn set-current-user [user]
  ;; connect websocket
  (let [socket (socket/make-ws-connection)]
    (rf/dispatch [:sessions/current-user user])))

(rf/reg-event-fx
 :sessions/action-sign-in
 (fn [db [_ username email]]
   (ajax/POST "/api/v1/sessions"
              {:params {:username username
                        :email email}
               :handler (fn [res]
                          (let [{:keys [user jwt]} res]
                            (.setItem js/localStorage "authToken" jwt)
                            (set-current-user user)
                            (url-location/goto "/")))
               :error-handler (fn [res]
                                (rf/dispatch [:sessions/session-error (:response res)]))})
   db))

(rf/reg-event-fx
 :sessions/action-set-user
 (fn [db _]
   (let [jwt (.getItem js/localStorage "authToken")]
     (ajax/GET "/api/v1/current-user"
               {:headers {"JWT-Token" jwt}
                :handler (fn [res]
                           (socket/make-ws-connection)
                           (let [{:keys [user]} res]
                             (rf/dispatch [:sessions/current-user user])))
                :error-handler (fn [res]
                                 (url-location/goto "/sign-in"))}))
   db))

(rf/reg-event-fx
 :sessions/action-sign-out
 (fn [db _]
   (.removeItem js/localStorage "authToken")
   (url-location/logout)
   db))
