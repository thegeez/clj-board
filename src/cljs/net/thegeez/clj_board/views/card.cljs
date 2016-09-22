(ns net.thegeez.clj-board.views.card
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [net.thegeez.clj-board.url-location :as url-location]))

(defn card-edit-form []
  (let [name (reagent/atom nil)
        description (reagent/atom nil)
        cancel-click (fn [e]
                       (.preventDefault e)
                       (dispatch [:current-board/show-card-edit-card-id nil]))]
    (fn [app-db card]
      (let [submit-form (fn [e]
                          (.preventDefault e)
                          (dispatch [:current-board/action-update-card
                                     (:id card)
                                     (or @name (:name card))
                                     (or @description (:description card))]))]
        [:div
         [:form
          {:on-submit submit-form}
          [:input {:id "name"
                   :type "text"
                   :size 21
                   :value (or @name (:name card))
                   :required true
                   :placeholder "Card title"
                   :on-change (fn [e]
                                (reset! name (-> e .-target .-value)))}]
          [:br]
          [:textarea
           {:id "description"
            :value (or @description (:description card))
            :required true
            :placeholder "Description"
            :on-change (fn [e]
                         (reset! description (-> e .-target .-value)))}]
          [:br]
          [:button {:type "submit"
                    :on-click submit-form}
           "Save card"]
          " or "
          [:a {:href "#"
               :on-click cancel-click}
           "cancel"]]]))))

(defn add-comment-form []
  (let [comment (reagent/atom nil)]
    (fn [card]
      (let [submit-form (fn [e]
                          (.preventDefault e)
                          (when-let [text @comment]
                            (dispatch [:current-board/action-add-comment (:id card) text])
                            (reset! comment nil)))]
        [:div
         [:h3 "Add comment"]
         [:form
          {:on-submit submit-form}
          [:textarea {:id "comment"
                      :value @comment
                      :required true
                      :placeholder "Write a comment"
                      :on-change (fn [e]
                                   (reset! comment (-> e .-target .-value)))}]
          [:br]
          [:button {:type "submit"
                    :on-click submit-form}
           "Save comment"]]]))))

(defn full-card []
  (fn [app-db card-id]
    (let [board (:current-board/board app-db)
          card (some (fn [c]
                       (when (= (:id c) card-id)
                         c))
                     (mapcat :cards (:lists board)))]
      [:div.board-overlay
       {:on-click (fn [e]
                    (.preventDefault e)
                    (.stopPropagation e)
                    (url-location/goto (:url board)))}
       [:div.full-card
        {:on-click (fn [e]
                     (.preventDefault e)
                     ;; to prevent propagation to overlay
                     (.stopPropagation e))}
        [:div.card-left
         (if (= (:id card) (:current-board/show-card-edit-card-id app-db))
           [card-edit-form app-db card]
           [:div
            [:h2.card-title (:name card)]
            [:div.tag-box
             [:h3 "Tags"]
             [:div.tag-row
              (for [t (:tags card)
                    :when (:enabled t)]
                [:div.mini-tag
                 {:key (:color t)
                  :className (:color t)}])]]
            [:div.members-box
             [:h3 "Members"]
             [:div.members
              (for [user (:members card)
                    :when (:assigned user)]
                [:div.member
                 {:key (:id user)}
                 (:username user)])]]
            [:div.clearfix]
            [:h3 "Description"]
            (:description card)
            [:br]
            [:a {:href "#"
                 :on-click (fn [e]
                             (.preventDefault e)
                             (dispatch [:current-board/show-card-edit-card-id (:id card)]))}
             "Edit"]])
         [add-comment-form card]

         (when-let [comments (seq (:comments card))]
           [:div
            [:h3 "Activity"]
            (for [comment comments]
              ^{:key (:id comment)}
              [:div {:id (:id comment)}
               (:text comment)
               [:br]
               "by: " (:username comment) " at "
               (.toLocaleString (js/Date. (:created_at comment)))
               [:br] [:br]])])]

        [:div.card-right.add-menu
         [:a.card-close-button
          {:href "#"
           :on-click (fn [e]
                       (.preventDefault e)
                       (.stopPropagation e)
                       (url-location/goto (:url board)))}
          [:i.fa.fa-close]]
         [:h3 "Add"]
         "Members"
         [:div
          [:div.dropdown
           [:button.btn.btn-default.dropdown-toggle
            {:data-toggle "dropdown"}
            [:i.fa.fa-user]
            " Members "]
           (into [:ul.dropdown-menu
                  [:li.dropdown-header
                   {:key "header"}
                   "Members"
                   [:i.fa.fa-close]]]
                 (for [user (:members board)]
                   (let [assigned (some (fn [member]
                                          (and (= (:id member) (:id user))
                                               (:assigned member)))
                                        (:members card))]
                     [:li {:key (:id user)}
                      [:a.members-dropdown
                       {:href "#"
                        :on-click (fn [e]
                                    (.preventDefault e)
                                    (dispatch [:current-board/toggle-card-member (:id card) (:id user) (not assigned)]))}
                       (:username user)
                       (if assigned
                         [:i.fa.fa-check]
                         [:i.fa.fa-times])]])))]]
         "Tags"
         [:div
          [:div.dropdown
           [:button.btn.btn-default.dropdown-toggle
            {:data-toggle "dropdown"}
            [:i.fa.fa-tag]
            " Tags "]
           (into [:ul.dropdown-menu
                  [:li.dropdown-header
                   {:key "header"}
                   "Tags"
                   [:i.fa.fa-close]]]
                 (for [t (:tags card)]
                   [:li {:key (:color t)}
                    [:div.tag
                     {:className (:color t)
                      :on-click (fn [e]
                                  (.preventDefault e)
                                  (dispatch [:current-board/toggle-tag (:id card) (:id t) (not (:enabled t))]))}
                     (if (:enabled t)
                       [:i.fa.fa-check]
                       [:i.fa.fa-times])]]))]]]]])))
