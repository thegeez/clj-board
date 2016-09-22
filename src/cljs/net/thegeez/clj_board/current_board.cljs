(ns net.thegeez.clj-board.current-board
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            [net.thegeez.clj-board.url-location :as url-location]
            [net.thegeez.clj-board.socket :as socket]))

(def init
  {:current-board/board nil
   :current-board/connected-users []
   :current-board/show-list-name-form false
   :current-board/list-name-form-errors {}
   :current-board/show-list-edit-form-list-id false
   :current-board/show-add-member-form false
   :current-board/add-member-form-errors {}
   :current-board/editing-list-id nil
   :current-board/adding-new-card-in-list-id nil
   :current-board/show-card-edit-card-id nil
   :current-board/error nil
   :current-board/fetching false})

(rf/reg-event-db
 :current-board/current-board-fetching
 (fn [db _]
   (assoc db :current-board/current-board-fetching true)))

(rf/reg-event-db
 :current-board/set-current-board
 (fn [db [_ board]]
   (assoc db
          :current-board/current-board-fetching false
          :current-board/editing-list-id nil
          :current-board/board board)))

(rf/reg-event-db
 :current-board/connected-users
 (fn [db [_ board-slug user-ids]]
   (cond-> db
     (= (:slug (:current-board/board db)) board-slug)
     (assoc :current-board/connected-users user-ids))))

(rf/reg-event-db
 :current-board/show-add-member-form
 (fn [db [_ bool]]
   (assoc db
          :current-board/show-add-member-form bool
          :current-board/add-member-form-errors {})))

(rf/reg-event-db
 :current-board/add-member-error
 (fn [db [_ error]]
   (assoc db :current-board/add-member-form-errors
          {:email error})))

(rf/reg-event-db
 :current-board/add-member
 (fn [db [_ board-slug user]]
   (rf/dispatch [:current-board/show-add-member-form false])
   (cond-> db
     (= (:slug (:current-board/board db)) board-slug)
     (update-in [:current-board/board :members] conj user))))

(rf/reg-event-db
 :current-board/show-list-name-form
 (fn [db [_ bool]]
   (assoc db
          :current-board/show-list-name-form bool
          :current-board/list-name-form-errors {})))

(rf/reg-event-db
 :current-board/list-name-error
 (fn [db [_ error]]
   (assoc db :current-board/list-name-form-errors
          {:name error})))

(rf/reg-event-db
 :current-board/action-connect-current-board
 (fn [db _]
   (let [[_ _ board-id] (:route db)]
     (socket/send-msg [:join-board board-id]))
   db))

(defmethod socket/handle-data :board
  [[_ board]]
  (rf/dispatch [:current-board/set-current-board board]))

(defmethod socket/handle-data :users
  [[_ board-slug user-ids]]
  (rf/dispatch [:current-board/connected-users board-slug user-ids]))

(defmethod socket/handle-data :add-member-error
  [[_ error]]
  (rf/dispatch [:current-board/add-member-error error]))

(defmethod socket/handle-data :add-member
  [[_ board-slug user]]
  (rf/dispatch [:current-board/add-member board-slug user]))

(rf/reg-event-db
 :current-board/action-add-member
 (fn [db [_ email]]
   (let [board-slug (:slug (:current-board/board db))]
     (socket/send-msg [:add-member board-slug email]))
   db))

(rf/reg-event-db
 :current-board/action-add-list
 (fn [db [_ name]]
   (let [board-slug (:slug (:current-board/board db))]
     (socket/send-msg [:add-list board-slug name]))
   db))

(defmethod socket/handle-data :add-list-error
  [[_ error]]
  (rf/dispatch [:current-board/list-name-error error]))

(rf/reg-event-db
 :current-board/list-created
 (fn [db [_ board-slug list]]
   (rf/dispatch [:current-board/show-list-name-form false])
   (cond-> db
     (= (:slug (:current-board/board db)) board-slug)
     (update-in [:current-board/board :lists] (fnil conj []) list))))

(defmethod socket/handle-data :list-created
  [[_ board-slug list]]
  (rf/dispatch [:current-board/list-created board-slug list]))

(rf/reg-event-db
 :current-board/action-update-list
 (fn [db [_ list-id position]]
   (let [board-slug (:slug (:current-board/board db))]
     (socket/send-msg [:update-list board-slug list-id position]))
   (update-in db [:current-board/board :lists]
              (fn [old]
                (mapv
                 (fn [c]
                   (if (= (:id c) list-id)
                     (assoc c :position position)
                     c))
                 old)))))

(defmethod socket/handle-data :list-updated
  [[_ board-slug board]]
  (rf/dispatch [:current-board/set-current-board board]))

(rf/reg-event-db
 :current-board/show-add-card-form
 (fn [db [_ list-id]]
   (assoc db
          :current-board/show-add-card-form-list-id list-id
          :current-board/add-card-form-errors {})))

(rf/reg-event-db
 :current-board/action-create-card
 (fn [db [_ name]]
   (let [board-slug (:slug (:current-board/board db))
         list-id (:current-board/show-add-card-form-list-id db)]
     (socket/send-msg [:create-card board-slug list-id name]))
   db))

(rf/reg-event-db
 :current-board/card-created
 (fn [db [_ board-slug card]]
   (rf/dispatch [:current-board/show-add-card-form nil])
   (cond-> db
     (= (:slug (:current-board/board db)) board-slug)
     (update-in [:current-board/board :lists]
                (fn [lists]
                  (for [list lists]
                    (if (= (:id list) (:list_id card))
                      (update-in list [:cards] conj card)
                      list)))))))

(defmethod socket/handle-data :card-created
  [[_ board-slug card]]
  (rf/dispatch [:current-board/card-created board-slug card]))

(rf/reg-event-db
 :current-board/action-update-card-location
 (fn [db [_ from-list-id to-list-id card-id position]]
   (let [board-slug (:slug (:current-board/board db))]
     (socket/send-msg [:update-card-location board-slug card-id to-list-id position]))
   (update-in db [:current-board/board :lists]
              (fn [old]
                (mapv
                 (fn [l]
                   (cond
                     (= (:id l) from-list-id to-list-id)
                     (update-in l [:cards]
                                (fn [cs]
                                  (mapv
                                   (fn [c]
                                     (if (= (:id c) card-id)
                                       (assoc c :position position)
                                       c))
                                   cs)))

                     (= (:id l) from-list-id)
                     (update-in l [:cards]
                                (fn [cs]
                                  (remove
                                   (fn [c]
                                     (= (:id c) card-id))
                                   cs)))

                     (= (:id l) to-list-id)
                     (let [card (->> (get-in db [:current-board/board :lists])
                                     (mapcat :cards)
                                     (some (fn [c]
                                             (when (= (:id c) card-id)
                                               c))))]
                       (update-in l [:cards] conj
                                  (assoc card
                                         :position position
                                         :list_id to-list-id)))

                     :else
                     l))
                 old)))))

(rf/reg-event-db
 :current-board/card-moved
 (fn [db [_ board-slug board]]
   (assoc db :current-board/board board)))

(defmethod socket/handle-data :card-moved
  [[_ board-slug board card]]
  (rf/dispatch [:current-board/card-moved board-slug board]))

(rf/reg-event-db
 :current-board/show-list-edit-form
 (fn [db [_ list-id]]
   (assoc db :current-board/show-list-edit-form-list-id list-id)))

(rf/reg-event-db
 :current-board/action-edit-list
 (fn [db [_ list-id name]]
   (let [board-slug (:slug (:current-board/board db))]
     (socket/send-msg [:edit-list board-slug list-id name]))
   db))

(defmethod socket/handle-data :list-edited
  [[_ board-slug board]]
  (rf/dispatch [:current-board/show-list-edit-form nil])
  (rf/dispatch [:current-board/set-current-board board]))

(rf/reg-event-db
 :current-board/show-card-edit-card-id
 (fn [db [_ card-id]]
   (assoc db :current-board/show-card-edit-card-id card-id)))

 (rf/reg-event-db
 :current-board/action-update-card
 (fn [db [_ card-id name description]]
   (let [board-slug (:slug (:current-board/board db))]
     (socket/send-msg [:update-card board-slug card-id name description]))
   db))

(defmethod socket/handle-data :card-edited
  [[_ board-slug board]]
  (rf/dispatch [:current-board/show-card-edit-card-id nil])
  (rf/dispatch [:current-board/set-current-board board]))

(rf/reg-event-db
 :current-board/action-add-comment
 (fn [db [_ card-id comment]]
   (let [board-slug (:slug (:current-board/board db))]
     (socket/send-msg [:add-comment board-slug card-id comment]))
   db))

(defmethod socket/handle-data :comment-added
  [[_ board-slug board]]
  (rf/dispatch [:current-board/set-current-board board]))

(rf/reg-event-db
 :current-board/toggle-tag
 (fn [db [_ card-id tag-id enable]]
   (let [board-slug (:slug (:current-board/board db))]
     (socket/send-msg [:toggle-tag board-slug card-id tag-id enable]))
   db))

(defmethod socket/handle-data :tag-toggled
  [[_ board-slug board]]
  (rf/dispatch [:current-board/set-current-board board]))

(rf/reg-event-db
 :current-board/toggle-card-member
 (fn [db [_ card-id user-id enable]]
   (let [board-slug (:slug (:current-board/board db))]
     (socket/send-msg [:toggle-card-member board-slug card-id user-id enable]))
   db))

(defmethod socket/handle-data :card-member-toggled
  [[_ board-slug board]]
  (rf/dispatch [:current-board/set-current-board board]))
