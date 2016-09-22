(ns net.thegeez.clj-board.boards
  (:require [io.pedestal.log :as log]
            [io.pedestal.interceptor :as interceptor]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]))

(defn board-to-resource [board]
  (assoc board
         :url (str "/boards/" (:id board) "-" (:slug board))))

(defn card-to-resource [board card]
  (assoc card
         :url (str "/boards/" (:id board) "-" (:slug board) "/cards/" (:id card))))

(defn get-boards [db user]
  (let [owned-boards (jdbc/query db ["Select * from boards b"])]
    {:owned-boards (map board-to-resource owned-boards)}))

(defn get-cards [db board list members]
  (let [cards (jdbc/query db ["select * from cards where list_id = ? order by position" (:id list)])]
    (for [card cards]
      (let [comments (jdbc/query db ["select c.id, username, text, c.created_at from comments c JOIN users u ON c.user_id = u.id where card_id = ?" (:id card)])
            tags (->> (jdbc/query db ["select * from tags t LEFT JOIN cards_tags ct ON ct.card_id = ? AND ct.tag_id = t.id" (:id card)])
                      (map (fn [t]
                             {:id (:id t)
                              :color (:color t)
                              :enabled (boolean (:card_id t))})))
            assigned-members-ids (->> (jdbc/query db ["select * from cards_users where card_id = ?" (:id card)])
                                      (map :user_id)
                                      set)
            members (for [member members]
                      (assoc member :assigned (contains? assigned-members-ids (:id member))))]
        (->> (assoc card
                    :comments comments
                    :tags tags
                    :members members)
             (card-to-resource board))))))

(defn get-board [db slug]
  ;; slug might have "999-" id preamble
  (let [slug (let [[id & rest] (string/split slug #"-")]
               (if (try (Long/parseLong id)
                        (catch Exception _ nil))
                 (string/join "-" rest)
                 slug))]
    (when-let [board (first (jdbc/query db ["Select * from boards where slug = ?" slug]))]
      (let [board-id (:id board)
            member-ids (-> #{(:user_id board)}
                           (into (map :user_id (jdbc/query db ["Select * from user_board where board_id = ?" board-id]))))
            members (map (fn [id]
                           (-> (jdbc/query db ["Select * from users where id = ?" id])
                               first
                               (select-keys [:id :username :email])))
                         member-ids)

            lists (for [list (jdbc/query db ["select * from lists where board_id = ? order by position" board-id])]
                    (assoc list
                           :cards (get-cards db board list members)))
            board (assoc board
                         :members members
                         :lists lists)]
        (board-to-resource board)))))

(defn get-board-for-card [db card]
  (first (jdbc/query db ["select * from boards b join lists l on b.id = l.board_id where l.id = ?" (:list_id card)])))

(defn create-board [db user name]
  (try
    (let [now (.getTime (java.util.Date.))
          slug (->> (-> name
                        (string/lower-case)
                        (string/replace #" " "-"))
                    (filter (set "abcdefghijklnmopqrstuvwxyz-"))
                    (apply str))]
      (jdbc/insert! db :boards
                    {:name name
                     :slug slug
                     :user_id (:id user)
                     :created_at now
                     :updated_at now})
      (get-board db slug))
    (catch Exception e
      ;; assume name/slug unique clash
      nil)))

(defn add-member [db board-slug email]
  (when-let [board (get-board db board-slug)]
    (if-let [user (first (jdbc/query db ["Select * from users where email = ?" email]))]
      (if-not (first (jdbc/query db ["Select * from user_board where user_id = ? and board_id = ?" (:id user) (:id board)]))
          (try
            (jdbc/insert! db :user_board
                          {:user_id (:id user)
                           :board_id (:id board)})
            (select-keys user [:id :email :username])
            (catch Exception _ {:error "Error while adding member"}))
          {:error "Email is of an already assigned member"})
      {:error "Email is not registered"})))

(defn create-list [db board-slug name]
  (try
    (when-let [board (get-board db board-slug)]
      (let [board-id (:id board)
            position (:position (first (jdbc/query db ["select position from lists where board_id = ? order by position desc"
                                                       ;; " limit 1" ;; derby doesn't support limit
                                                       board-id])))
            position (if position
                       (+ position 1024)
                       1024)]
        (jdbc/insert! db :lists
                      {:name name
                       :board_id (:id board)
                       :position position})
        (let [list (first (jdbc/query db ["select * from lists where board_id = ? and position = ?" board-id position]))]
          list)))
    (catch Exception e
      {:error "Can't create list"})))

(defn update-list [db list-id position]
  (jdbc/update! db :lists
                {:position position}
                ["id = ?" list-id]))

(defn edit-list [db list-id name]
  (jdbc/update! db :lists
                {:name name}
                ["id = ?" list-id]))

(defn get-card [db card-id]
  (first (jdbc/query db ["select * from cards where id = ?" card-id])))

(defn create-card [db list-id name]
  (try
    (let [position (:position (first (jdbc/query db ["select position from cards where list_id = ? order by position desc"
                                                     ;; " limit 1" ;; derby doesn't support limit
                                                     list-id])))
          position (if position
                     (+ position 1024)
                     1024)
          res (first (jdbc/insert! db :cards
                                   {:list_id list-id
                                    :name name
                                    :position position}))
          created-id (or (:1 res) ;;derby
                         (:id res) ;;psql
                         )
          _ (log/info :created-id created-id)
          card (get-card db created-id)
          board (get-board-for-card db card)]
      (card-to-resource board card))
    (catch Exception _ nil)))

(defn move-card [db card-id list-id position]
  (try
    (jdbc/update! db :cards
                  {:list_id list-id
                   :position position}
                  ["id = ?" card-id])
    (let [card (get-card db card-id)
          board (get-board-for-card db card)]
      (card-to-resource board card))
    (catch Exception _ nil)))

(defn edit-card [db card-id name description]
  (jdbc/update! db :cards
                {:name name
                 :description description}
                ["id = ?" card-id]))

(defn add-comment [db card-id user-id comment]
  (jdbc/insert! db :comments
                {:card_id card-id
                 :user_id user-id
                 :text comment
                 :created_at (.getTime (java.util.Date.))}))

(defn toggle-tag [db card-id tag-id enable]
  (if enable
    (jdbc/insert! db :cards_tags
                  {:card_id card-id
                   :tag_id tag-id})
    (jdbc/delete! db :cards_tags
                  ["card_id = ? AND tag_id = ?" card-id tag-id])))

(defn toggle-card-member [db card-id user-id enable]
  (if enable
    (jdbc/insert! db :cards_users
                  {:card_id card-id
                   :user_id user-id})
    (jdbc/delete! db :cards_users
                  ["card_id = ? AND user_id = ?" card-id user-id])))

(def boards
  (interceptor/interceptor
   {:enter (fn [context]
             (let [user (:user context)
                   {:keys [owned-boards invited-boards]} (get-boards (:database context) (:user context))]
               (assoc context :response
                      {:status 200
                       :body {:owned-boards owned-boards
                              :invited-boards invited-boards}})))}))

(def create
  (interceptor/interceptor
   {:enter (fn [context]
             (let [user (:user context)
                   name (get-in context [:request :transit-params :board :name])]
               (if-let [board (create-board (:database context)
                                            user
                                            name)]
                 (assoc context :response
                                 {:status 201
                                  :body {:board
                                         board}})
                 (assoc context :response
                                 {:status 422
                                  :body {:errors {:name "Name invalid"}}}))))}))



