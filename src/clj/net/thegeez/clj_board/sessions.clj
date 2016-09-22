(ns net.thegeez.clj-board.sessions
  (:require [io.pedestal.log :as log]
            [io.pedestal.interceptor :as interceptor]
            [clojure.java.jdbc :as jdbc]
            [net.thegeez.clj-board.jwt :as jwt]))

(defn find-or-create-user [db username email]
  (log/info :foc username :e email)
  (if-let [user (first (jdbc/query db ["Select * from users where email = ?" email]))]
    {:id (:id user)
     :username (:username user)
     :email (:email user)}
    (let [now (.getTime (java.util.Date.))
          user-id (first (map (fn [r]
                                (or (:1 r)
                                    (:id r)))
                              (jdbc/insert! db :users
                                            {:username username
                                             :email email
                                             :created_at now
                                             :updated_at now})))
          demo-board-id (:id (first (jdbc/query db ["select id from boards where slug = ?" "demo-board"])))]
      ;; assign to demo board
      (jdbc/insert! db :user_board
                    {:user_id user-id
                     :board_id demo-board-id})
      (find-or-create-user db username email))))



(def create
  (interceptor/interceptor
   {:enter (fn [context]
             (let [{:keys [username email]} (get-in context [:request :transit-params])]
               (let [user (find-or-create-user (:database context)
                                               username
                                               email)]
                 (assoc context :response
                        {:status 201
                         :body {:user user
                                :jwt (jwt/jwt-encode user)}})
                 ;; find-or-create-user will always succeed
                 #_(assoc context :response
                        {:status 422
                         :body {:error "Invalid username, email or password."}}))))}))

(def get-user
  (interceptor/interceptor
   {:enter (fn [context]
             (assoc context :response
                    {:status 200
                     :body {:user (:user context)}}))}))
