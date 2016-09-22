(ns net.thegeez.clj-board.core
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync] :as rf]
            [secretary.core :as secretary :refer-macros [defroute]]
            [net.thegeez.clj-board.current-board :as current-board]
            [net.thegeez.clj-board.boards :as boards]
            [net.thegeez.clj-board.sessions :as sessions]
            [net.thegeez.clj-board.views :as views]
            [net.thegeez.clj-board.url-location :as url-location]))

(set-print-fn! (fn [_] nil)) ;; enabled in dev

(defroute "/" [] (dispatch [:route [:home]]))
(defroute "/boards/:board-id" [board-id] (dispatch [:route [:home :boards board-id]]))
(defroute "/boards/:board-id/cards/:card-id" [board-id card-id] (dispatch [:route [:home :boards board-id :card (js/parseInt card-id)]]))
(defroute "/sign-in" [] (dispatch [:route [:sign-in]]))

(rf/reg-event-db
 :route
 (fn [db [_ path]]
   (if (and (not (:sessions/current-user db))
            (not (#{[:sign-in] [:sign-up]} path)))
     (if-let [jwt (.getItem js/localStorage "authToken")]
       (do (rf/dispatch [:sessions/action-set-user])
           (assoc db :route path))
       (url-location/goto "/sign-in"))
     (assoc db :route path))))

;; root subscription, don't bother with more fine grained subscriptions
(rf/reg-sub
  :root
  (fn [db _]
    db))

(rf/reg-event-db
 :init
 (fn [db _]
   (merge sessions/init
          boards/init
          current-board/init
          db)))

(defn ^:export main
  []
  (views/init)
  (dispatch-sync [:init])
  (url-location/load)
  (reagent/render [views/app]
                  (.getElementById js/document "app")))

