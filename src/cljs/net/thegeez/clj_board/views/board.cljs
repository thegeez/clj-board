(ns net.thegeez.clj-board.views.board
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [net.thegeez.clj-board.views.card :as card]
            [net.thegeez.clj-board.url-location :as url-location])
  (:import [ReactDnD.DragDropContext]
           [ReactDnD.DropTarget]
           [ReactDnD.DragSource]
           [ReactDnDHTML5Backend]))

(defn add-member-form []
  (let [email (reagent/atom nil)
        cancel-click (fn [e]
                       (.preventDefault e)
                       (dispatch [:current-board/show-add-member-form false]))
        submit-form (fn [e]
                      (.preventDefault e)
                      (dispatch [:current-board/action-add-member @email]))]
    (fn [app-db]
      [:div.dropdown
       "Add new member"
       [:form
        {:on-submit submit-form}
        [:input {:id "email"
                 :type "text"
                 :value @email
                 :required true
                 :placeholder "Member email address"
                 :on-change (fn [e]
                              (reset! email (-> e .-target .-value)))}]
        (when-let [err (:email (:current-board/add-member-form-errors app-db))]
          [:div.error
           err])
        [:button {:type "submit"}
         "Add member"]
        " or "
        [:a {:href "#"
             :on-click cancel-click}
         "cancel"]]])))

(defn list-name-form []
  (let [name (reagent/atom nil)
        cancel-click (fn [e]
                       (.preventDefault e)
                       (dispatch [:current-board/show-list-name-form false]))
        submit-form (fn [e]
                      (.preventDefault e)
                      (dispatch [:current-board/action-add-list @name]))]
    (fn [app-db]
      [:div
       [:form
        {:on-submit submit-form}
        [:input {:auto-focus true
                 :id "name"
                 :type "text"
                 :value @name
                 :size 16
                 :required true
                 :placeholder "Add a new list..."
                 :on-change (fn [e]
                              (reset! name (-> e .-target .-value)))}]
        (when-let [err (:name (:current-board/list-name-form-errors app-db))]
          [:div.error
           err])
        [:button {:type "submit"}
         "Add list"]
        " or "
        [:a {:href "#"
             :on-click cancel-click}
         "cancel"]]])))

(defn add-list-button []
  (fn [app-db]
    (if (:current-board/show-list-name-form app-db)
      [list-name-form app-db]
      [:div
       [:a {:href "#"
            :onClick (fn [e]
                       (.preventDefault e)
                       (dispatch [:current-board/show-list-name-form true]))}
        "Add new list.."]])))

(defn edit-list-form []
  (let [name (reagent/atom nil)
        cancel-click (fn [e]
                       (.preventDefault e)
                       (dispatch [:current-board/show-list-edit-form nil]))]
    (fn [app-db list-id list-name]
      [:div.edit-list-form
       [:form
        {:on-submit (fn [e]
                      (.preventDefault e)
                      (when-let [name @name]
                        (dispatch [:current-board/action-edit-list list-id name])))}
        [:input {:id "name"
                 :type "text"
                 :value (or @name list-name)
                 :size 14
                 :auto-focus true
                 :required true
                 :placeholder "List name"
                 :on-change (fn [e]
                              (reset! name (-> e .-target .-value)))}]
        [:button {:type "submit"}
         "Save"]
        " or "
        [:a {:href "#"
             :on-click cancel-click}
         "cancel"]]])))

(defn edit-list-button []
  (fn [app-db list]
    (let [list-id (:id list)
          list-name (:name list)]
      (if (= (:current-board/show-list-edit-form-list-id app-db) list-id)
        [edit-list-form app-db list-id list-name]
        [:h1.list-title
         {:onClick (fn [e]
                     (.preventDefault e)
                     (dispatch [:current-board/show-list-edit-form list-id]))}
         list-name]))))

(defn add-card-form []
  (let [name (reagent/atom nil)
        cancel-click (fn [e]
                       (.preventDefault e)
                       (dispatch [:current-board/show-add-card-form nil]))
        submit-form (fn [e]
                      (.preventDefault e)
                      (dispatch [:current-board/action-create-card @name]))]
    (fn [app-db list-id]
      [:div.add-card-form
       [:form
        {:on-submit submit-form}
        [:input {:id "name"
                 :type "text"
                 :value @name
                 :size 14
                 :auto-focus true
                 :required true
                 :placeholder "Card name"
                 :on-change (fn [e]
                              (reset! name (-> e .-target .-value)))}]
        (when-let [err (:name (:current-board/add-card-form-errors app-db))]
          [:div.error
           err])
        [:button {:type "submit"}
         "Add"]
        " or "
        [:a {:href "#"
             :on-click cancel-click}
         "cancel"]]])))

(defn add-card-button []
  (fn [app-db list-id]
    (if (= (:current-board/show-add-card-form-list-id app-db) list-id)
      [add-card-form app-db]
      [:div.add-card-button
       [:a
        {:href "#"
         :onClick (fn [e]
                    (.preventDefault e)
                    (dispatch [:current-board/show-add-card-form list-id]))}
        "Add new card.."]])))

(defn drag-card []
  (let [class (.createClass reagent/react
                            #js {:render (fn []
                                           (this-as this
                                             (let [props (.-props this)
                                                   app-db (.-wrap props)
                                                   list-id (.-list_id props)
                                                   card-id (.-id props)
                                                   children (.-children props)
                                                   connect-drag-source (.-connectDragSource props)
                                                   connect-drop-target (.-connectDropTarget props)
                                                   is-over (.-isOver props)
                                                   is-dragging (.-isDragging props)
                                                   button (.-button props)
                                                   connect-dragging (if button
                                                                      connect-drop-target
                                                                      (comp connect-drag-source
                                                                            connect-drop-target))
                                                   card (.-card props)]

                                               (connect-dragging
                                                (reagent/as-element
                                                 [:div
                                                  {:style {:height (if button
                                                                     (str
                                                                      (+ (if is-over
                                                                           70
                                                                           10)
                                                                         (if (= (:current-board/show-add-card-form-list-id app-db) list-id)
                                                                           50
                                                                           0))
                                                                      "px")
                                                                     (if is-over
                                                                       "200px"
                                                                       "100px"))
                                                           :display (if is-dragging
                                                                      "none"
                                                                      "block")}}
                                                  (when is-over
                                                    [:div.card-drag-filler])
                                                  (if button
                                                    button
                                                    [:div.drag-card
                                                     {:on-click (fn [e]
                                                                  (.preventDefault e)
                                                                  (url-location/goto
                                                                   (:url card)))}
                                                     (:name card)
                                                     [:div.tag-row
                                                      (for [t (:tags card)
                                                            :when (:enabled t)]
                                                        [:div.line-tag
                                                         {:key (:color t)
                                                          :className (:color t)}])]
                                                     (let [c (count (:comments card))]
                                                       (when (pos? c)
                                                         [:div.comment-count
                                                          [:i.fa.fa-comment] " " c]))
                                                     (let [assigned-members (filter :assigned (:members card))
                                                           c (count assigned-members)]
                                                       (when (pos? c)
                                                         [:div.card-members
                                                          {:title (map :username assigned-members)}
                                                          [:i.fa.fa-users] " " c]))
                                                     (let [[_ _board board-id _card route-card-id] (:route app-db)]
                                                       (when (= route-card-id card-id)
                                                         [card/full-card app-db (:id card)]))]
                                                    )])))))})
        card-source #js {:beginDrag (fn [props]
                                      #js {:id (.-id props)
                                           :list_id (.-list_id props)
                                           :name (.-name props)
                                           :position (.-position props)})
                         :isDragging (fn [props monitor]
                                       (= (.-id props)
                                          (.. monitor getItem -id)))}
        card-source-collect (fn [connect monitor]
                              #js {:connectDragSource (.dragSource connect)
                                   :isDragging (.isDragging monitor)})
        ds (js/ReactDnD.DragSource "card"
                                   card-source
                                   card-source-collect)
        card-target #js {:drop (fn [target-props monitor]
                                 (let [item (.getItem monitor)
                                       drag-id (.-id item)
                                       drag-list-id (.-list_id item)
                                       target-id (.-id target-props)
                                       target-list-id (.-list_id target-props)
                                       move-card (.-moveCard target-props)]
                                   (move-card [drag-list-id drag-id]
                                              [target-list-id target-id]))
                                 #js {})}
        card-target-collect (fn [connect monitor]
                              #js {:connectDropTarget (.dropTarget connect)
                                   :isOver (.isOver monitor)})
        dt (js/ReactDnD.DropTarget "card"
                                   card-target
                                   card-target-collect)]
    (ds (dt class))))

(defn card-list []
  (fn [app-db list-id]
    (let [list (some
                (fn [list]
                  (when (= (:id list) list-id)
                    list))
                (:lists (:current-board/board app-db)))
          cards (->> (:cards list)
                     (sort-by :position))
          cards (concat cards
                        [{:id -1
                          :list_id list-id
                          :position (if-let [l (last cards)]
                                      (+ (:position l) 1024)
                                      1024)
                          :button (reagent/as-element
                                   [add-card-button app-db list-id])}])
          move-card (fn [[drop-list-id drop-card-id]
                         [target-list-id target-card-id]]
                      (let [[before after]
                            (->> (into [nil] cards)
                                 (partition-all 2 1)
                                 (some (fn [[b a]]
                                         (when (= (:id a) target-card-id)
                                           [b a]))))

                            position (cond
                                       (= (:id before) drop-card-id)
                                       nil

                                       (nil? before)
                                       (/ (:position after)
                                          2)

                                       :else
                                       (/ (+ (:position before)
                                             (:position after))
                                          2))]
                        (when position
                          (dispatch-sync [:current-board/action-update-card-location
                                          drop-list-id
                                          target-list-id
                                          drop-card-id
                                          position]))))]
      [:div
       [edit-list-button app-db list]
       (for [card cards]
         (reagent/create-element
          drag-card
          #js {:wrap app-db
               :id (:id card)
               :list_id (:list_id card)
               :card card
               :key (:id card)
               :button (:button card)
               :moveCard move-card
               }))])
    ))

(defn list-column []
  (let [class (.createClass reagent/react
                            #js {:render (fn []
                                           (this-as this
                                             (let [props (.-props this)
                                                   app-db (.-wrap props)
                                                   list-id (.-id props)
                                                   children (.-children props)
                                                   connect-drag-source (.-connectDragSource props)
                                                   connect-drop-target (.-connectDropTarget props)
                                                   connect-card-drop-target (.-connectCardDropTarget props)
                                                   is-over (.-isOver props)
                                                   is-dragging (.-isDragging props)
                                                   button (.-button props)
                                                   connect-dragging (if button
                                                                      connect-drop-target
                                                                      (comp connect-drag-source
                                                                            connect-drop-target
                                                                            connect-card-drop-target)) ]
                                               (connect-dragging
                                                (reagent/as-element
                                                 [:div
                                                  {:style {:width (if is-over
                                                                    "340px"
                                                                    "170px")
                                                           :float "left"
                                                           :display (if is-dragging
                                                                      "none"
                                                                      "block")
                                                           }}
                                                  (when is-over
                                                    [:div.list-drag-filler])
                                                  (if button
                                                    [:div.list-button
                                                     button]
                                                    [:div.list-column
                                                     ;;children
                                                     [card-list app-db list-id]])])))))})
        list-source #js {:beginDrag (fn [props]
                                      #js {:id (.-id props)
                                           :name (.-name props)
                                           :position (.-position props)})
                         :isDragging (fn [props monitor]
                                       (= (.-id props)
                                          (.. monitor getItem -id)))}
        list-source-collect (fn [connect monitor]
                              #js {:connectDragSource (.dragSource connect)
                                   :isDragging (.isDragging monitor)})
        ds (js/ReactDnD.DragSource "list"
                                   list-source
                                   list-source-collect)
        list-target #js {:drop (fn [target-props monitor]
                                 (let [drag-id (.. monitor getItem -id)
                                       target-id (.-id target-props)
                                       move-list (.-moveList target-props)]
                                   (move-list drag-id target-id))
                                 #js {})}
        list-target-collect (fn [connect monitor]
                              #js {:connectDropTarget (.dropTarget connect)
                                   :isOver (.isOver monitor)})
        dt (js/ReactDnD.DropTarget "list"
                                   list-target
                                   list-target-collect)

        card-target #js {:drop (fn [target-props monitor]
                                 (let [source-props (.getItem monitor)
                                       source-id (.-id source-props)]
                                   #js {}))}
        card-target-collect (fn [connect monitor]
                              #js {:connectCardDropTarget (.dropTarget connect)})
        dtc (js/ReactDnD.DropTarget "card"
                                    card-target
                                    card-target-collect)]
    (ds (dt (dtc class)))))

(def board
  (.createClass reagent/react
                #js {:componentWillMount (fn []
                                           (dispatch [:current-board/current-board-fetching]))
                     :componentDidMount (fn []
                                          (dispatch [:current-board/action-connect-current-board]))
                     :componentWillUpdate (fn [next-props]
                                            (this-as this
                                              (when (and next-props
                                                         (.-props this))
                                                (let [old-app-db (.-wrap (.-props this))
                                                      next-app-db (.-wrap next-props)]
                                                  (when-not (= (let [[_ _board board-id _card card-id] (:route old-app-db)]
                                                                 board-id)
                                                               (let [[_ _board board-id _card card-id] (:route next-app-db)]
                                                                 board-id))
                                                    (dispatch [:current-board/action-connect-current-board]))))))
                     :render (fn []
                               (this-as this
                                 (let [app-db (.-wrap (.-props this))
                                       children (.-children (.-props this))]
                                   (reagent/as-element
                                    (if (:current-board/current-board-fetching app-db)
                                      [:h3 [:i.fa {:class "fa-spinner fa-spin"}]]
                                      (let [board (:current-board/board app-db)]
                                        [:div.board-content
                                         [:div.row.board-header
                                          [:div.board-name (:name board)]
                                          [:div.members
                                           (let [user-ids (set (:current-board/connected-users app-db))]
                                             (concat (for [user (:members board)]
                                                       (let [user (cond-> user
                                                                    (some #{(:id user)} user-ids)
                                                                    (assoc :connected true))]
                                                         [:div.member {:key (:id user)
                                                                       :title (if (:connected user)
                                                                                "Online"
                                                                                "Offline")
                                                                       :class (when-not (:connected user)
                                                                                "offline")}
                                                          (:username user)]))
                                                     [[:div.add-member-form.dropdown
                                                       {:key "add-member-form"}
                                                       [:a.btn.btn-default.dropdown-toggle
                                                        {:data-toggle "dropdown"
                                                         :key "add-member-button"
                                                         :on-click
                                                         (fn [e]
                                                           (.preventDefault e)
                                                           (dispatch [:current-board/show-add-member-form true]))}
                                                        [:i.fa.fa-plus]]
                                                       (when (:current-board/show-add-member-form app-db)
                                                         [add-member-form app-db])]]))]]

                                         (let [list (->> (:lists board)
                                                         (sort-by :position))
                                               list (concat list
                                                            [{:id -1
                                                              :position (if-let [l (last list)]
                                                                          (+ (:position l) 1024)
                                                                          1024)
                                                              :button (reagent/as-element
                                                                       [add-list-button app-db])}])]
                                           [:div.board
                                            [:div.list-wrapper
                                             [:div.lists
                                              {:style {:width (* (count list) 170)}}
                                              (for [column list]
                                                (reagent/create-element
                                                 list-column
                                                 #js {:button (:button column)
                                                      :moveList (fn [drag-id hover-id]

                                                                  (let [[before after]
                                                                        (->> (into [nil] list)
                                                                             (partition-all 2 1)
                                                                             (some (fn [[b a]]
                                                                                     (when (= (:id a) hover-id)
                                                                                       [b a]))))

                                                                        position (cond
                                                                                   (= (:id before) drag-id)
                                                                                   nil

                                                                                   (nil? before)
                                                                                   (/ (:position after)
                                                                                      2)

                                                                                   ;; there's always an after because of the button
                                                                                   ;; (nil? after)
                                                                                   ;; (do
                                                                                   ;;   (+ (:position before)
                                                                                   ;;      1024))
                                                                                   :else
                                                                                   (/ (+ (:position before)
                                                                                         (:position after))
                                                                                      2))]
                                                                    (when position
                                                                      ;; update ui as quick as possible to avoid jitter
                                                                      (dispatch-sync [:current-board/action-update-list drag-id position]))))
                                                      :position (:position column)
                                                      :id (:id column)
                                                      :key (:id column)
                                                      :wrap app-db})
                                                )]]
                                            children])]))))))}))

(defn wrap-board []
  (let [be js/ReactDnDHTML5Backend
        dc (js/ReactDnD.DragDropContext
            be)
        rc (dc board)]
    rc))
