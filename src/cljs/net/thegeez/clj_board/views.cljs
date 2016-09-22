(ns net.thegeez.clj-board.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [re-frame.db :as db]
            [secretary.core :as secretary]
            [net.thegeez.clj-board.views.board :as board]
            [net.thegeez.clj-board.url-location :as url-location]))

(def alphabet-chars (set "abcdefghijklmnopqrstuvwxyz0123456789-_"))

(def footer [:div.footer
             [:div.container
              "clj-board by " [:a {:href "http://thegeez.net"}
                               "thegeez.net"] " - " [:a {:href "https://twitter.com/thegeez"}
                               "@thegeez"]]])

(defn sign-in []
  (let [form-values (reagent/atom {:username "Demo User"
                                   :email "demo-user@clj-board.thegeez.net"})

        set-username (fn [e]
                       (let [username (-> e .-target .-value)
                             email (str (apply str (->> username
                                                        .toLowerCase
                                                        (map (fn [c]
                                                               (if (= c \space)
                                                                 "-"
                                                                 c)))
                                                        (filter alphabet-chars)))
                                        "@clj-board.thegeez.net")]
                         (swap! form-values assoc
                                :username username
                                :email email)))
        handle-submit (fn [e]
                        (.preventDefault e)
                        (let [{:keys [username email]} @form-values]
                          (dispatch [:sessions/action-sign-in username email])))]
    (fn [app-db]
      [:div.container
       [:div.row
        [:div.col-md-6.col-md-offset-3
         [:h1 "clj-board"]
         [:h2 "A drag & drop \"kanban board\" example with Clojure"]
         [:div.panel.panel-default
          [:div.panel-heading
           [:h2.panel-title "Sign in"]]
          [:div.panel-body
           [:p "This is a demo application. You can login with any username, the email address will be generated for you. All data in this demo system is public!"]
           [:form {:on-submit handle-submit}
            (when-let [error (:error app-db)]
              [:div.error error])
            [:div.form-group
             [:label {:for "username"} "Username"]
             [:input.form-control
              {:type "text"
               :id "username"
               :required true
               :value (:username @form-values)
               :on-change set-username}]]
            [:div.form-group
             [:label {:for "email"} "Email"]
             [:input.form-control
              {:type "email"
               :id "email"
               :size 20
               :disabled true
               :value (:email @form-values)}]]
            [:button {:class "btn btn-default"
                      :type "submit"}
             "Sign in"]]]]
         footer]]])))

(defn add-board-form []
  (let [name (reagent/atom nil)
        cancel-click (fn [e]
                       (.preventDefault e)
                       (dispatch [:boards/board-show-form false]))
        submit-form (fn [e]
                      (.preventDefault e)
                      (dispatch [:boards/action-create-board @name]))]
    (fn [app-db]
      [:div.add-board-button
       [:form
        {:on-submit submit-form}
        [:input {:id "name"
                 :type "text"
                 :size 15
                 :value @name
                 :required true
                 :placeholder "Board name"
                 :on-change (fn [e]
                              (reset! name (-> e .-target .-value)))}]
        (when-let [err (:name (:boards/form-errors app-db))]
          [:div.error
           err])
        [:button {:type "submit"}
         "Create"]
        " or "
        [:a {:href "#"
             :on-click cancel-click}
         "cancel"]]])))

(defn add-board-button []
  (fn [app-db]
    (if (:boards/show-add-board-form app-db)
      [add-board-form app-db]
      [:a.add-board-button
       {:href "#"
        :onClick (fn [e]
                   (.preventDefault e)
                   (dispatch [:boards/board-show-form true]))}
       "Add new board.."])))

(defn board-button [board]
  [:a.board-button {:key (:id board)
       :href (:url board)
       :on-click url-location/link}
   (:name board)])

(defn home []
  (fn [app-db]
    (let [{:keys [boards/owned-boards]} app-db]
      [:div
       (if (:boards/fetching app-db)
         [:h3 [:i.fa.fa-spinner.fa-spin]]
         [:div.boards-wrapper
          [:div.boards
           (when (seq owned-boards)
             [:section {:key "owned-boards"}
              [:h1
               [:i.fa.fa-user]
               " My boards"]
              (concat (map board-button owned-boards)
                      [^{:key "add-board-button"}
                       [add-board-button app-db]])])
           [:div.clearfix]]])])))

(defn authenticated []
  (do
    (dispatch [:boards/action-fetch-boards])
    (fn [app-db children]
      [:div.wrapper
       [:div.navbar.navbar-static-top
        [:div.container
         [:div.dropdown
          [:button.btn.btn-default.dropdown-toggle
           {:data-toggle "dropdown"}
           [:i.fa.fa-columns]
           "Boards "
           [:span.caret]]
          (into [:ul.dropdown-menu]
                (concat (for [b (:boards/owned-boards app-db)]
                          [:li {:key (:slug b)}
                           [:a.menuitem
                            {:href (:url b)
                             :on-click url-location/link}
                            (:name b)]])
                        [[:li.divider]
                         [:li [:a {:href "/"
                                   :on-click url-location/link}
                               "All boards"]]]))]

         [:span
          [:a.navbar-brand
           {:href "/"
            :on-click url-location/link}
           "clj-board"]]
         [:ul.nav.navbar-nav.pull-right
          [:li
           (let [user (:sessions/current-user app-db)]
             [:a (:username user)])]
          [:li [:a {:href "#"
                    :on-click (fn [e]
                                (.preventDefault e)
                                (dispatch [:sessions/action-sign-out]))} "Sign out"]]]]]

       children
       footer]
      )))

(defn router [app-db]
  (let [route (:route app-db)]
    (condp = (first (:route app-db))
      :sign-in
      [sign-in app-db]
      :home
      [authenticated
       app-db
       ;;[:h1 "app-db" app-db]
       (let [[_ _board board-id _card card-id] route]
         (if-not board-id
           [home app-db]
           (do
             (reagent/create-element board/wrap-board
                                     #js {:wrap app-db}))))]
      ;; flashes on screen on init
      [:div])))

(defn app
  []
  (let [app-db (subscribe [:root])]
    (fn []
      (let [app-db @app-db]
        [router app-db]))))

(defn init []
  ;; workaround for the late availability of the react-dnd lib
  (set! board/drag-card (board/drag-card))
  (set! board/list-column (board/list-column))
  (set! board/wrap-board (board/wrap-board)))
