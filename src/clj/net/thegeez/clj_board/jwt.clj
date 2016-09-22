(ns net.thegeez.clj-board.jwt
  (:require [io.pedestal.log :as log]
            [io.pedestal.interceptor :as interceptor]
            [clj-jwt.core  :as jwt]))

(def jwt-secret "demo_unsafe")

(defn jwt-encode [user]
  (-> user
      jwt/jwt
      (jwt/sign :HS256 jwt-secret)
      jwt/to-str))

(defn jwt-decode-to-user [s]
  (let [jwt (jwt/str->jwt s)]
    (when (jwt/verify jwt :HS256 jwt-secret)
      (:claims jwt))))

(def require-user
  (interceptor/interceptor
   {:enter (fn [context]
             (let [jwt (get-in context [:request :headers "jwt-token"])]
               (if-let [user (and jwt
                                  (jwt-decode-to-user jwt))]
                 (assoc context :user user)
                 (assoc context :response
                        {:status 422
                         :body {:error "Invalid token"}}))))}))
