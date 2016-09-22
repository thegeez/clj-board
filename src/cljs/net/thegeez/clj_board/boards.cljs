(ns net.thegeez.clj-board.boards
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            [net.thegeez.clj-board.url-location :as url-location]))

(def init
  {:boards/owned-boards []
   :boards/invited-boards []
   :boards/show-add-board-form false
   :boards/form-errors nil
   :boards/fetching false})

(rf/reg-event-db
 :boards/boards-fetching
 (fn [db _]
   (assoc db :boards/fetching true)))

(rf/reg-event-db
 :boards/boards-received
 (fn [db [_ {:keys [owned-boards invited-boards]}]]
   (assoc db
          :boards/fetching false
          :boards/owned-boards owned-boards
          :boards/invited-boards invited-boards)))

(rf/reg-event-db
 :boards/board-show-form
 (fn [db [_ bool]]
   (assoc db
          :boards/show-add-board-form bool
          :boards/form-errors nil)))

(rf/reg-event-db
 :boards/boards-create-error
 (fn [db [_ errors]]
   (assoc db :boards/form-errors errors)))

(rf/reg-event-db
 :boards/boards-new-board-created
 (fn [db [_ board]]
   (update-in db [:boards/owned-boards] conj board)))

(rf/reg-event-fx
 :boards/action-fetch-boards
 (fn [db _]
   (rf/dispatch [:boards/boards-fetching])
   (let [jwt (.getItem js/localStorage "authToken")]
     (ajax/GET "/api/v1/boards"
               {:headers {"JWT-Token" jwt}
                :handler (fn [res]
                           (let [{:keys [owned-boards invited-boards]} res]
                             (rf/dispatch [:boards/boards-received {:owned-boards owned-boards
                                                                    :invited-boards invited-boards}])))
                :error-handler (fn [res]
                                 (println "fetchBoards fail"))}))
   db))

(rf/reg-event-db
 :boards/action-create-board
 (fn [db [_ name]]
   (let [jwt (.getItem js/localStorage "authToken")]
     (ajax/POST "/api/v1/boards"
                {:headers {"JWT-Token" jwt}
                 :params {:board {:name name}}
                 :handler (fn [res]
                            (let [board (:board res)]
                              (rf/dispatch [:boards/board-show-form false])
                              (rf/dispatch [:boards/boards-new-board-created
                                            board])
                              (url-location/goto (:url board))))
                 :error-handler (fn [res]
                                  (rf/dispatch [:boards/boards-create-error
                                                (:errors (:response res))]))}))
   db))
