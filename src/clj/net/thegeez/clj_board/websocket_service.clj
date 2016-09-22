(ns net.thegeez.clj-board.websocket-service
  (:require [io.pedestal.log :as log]
            [net.thegeez.clj-board.boards :as boards]
            [net.thegeez.clj-board.jwt :as jwt]))

(defn handler [database listener reply broadcast remover send-ws]
  (fn [token connection-id msg]
    (let [user (jwt/jwt-decode-to-user token)
          msg-type (first msg)]
      (log/info :msg (str "A client sent - " msg " - from token " token))
      (cond
        (= msg-type :heart-beat)
        (send-ws token [:heart-beat])

        (= msg-type :join-board)
        (let [slug (second msg)
              board (boards/get-board database slug)]
          (when board
            (send-ws token [:board board])
            (listener (:slug board) token connection-id user)))

        (= msg-type :add-member)
        (let [[_ board-slug email] msg]
          (let [user-or-error (boards/add-member database board-slug email)]
            (if-let [error (:error user-or-error)]
              (reply token [:add-member-error error])
              (let [user user-or-error]
                (broadcast board-slug [:add-member board-slug user])))))

        (= msg-type :add-list)
        (let [[_ board-slug name] msg]
          (let [list-or-error (boards/create-list database board-slug name)]
            (if-let [error (:error list-or-error)]
              (reply token [:add-list-error error])
              (let [list list-or-error]
                (broadcast board-slug [:list-created board-slug list])))))

        (= msg-type :update-list)
        (let [[_ board-slug list-id position] msg]
          (let [list (boards/update-list database list-id position)
                board (boards/get-board database board-slug)]
            (broadcast board-slug [:list-updated board-slug board])))

        (= msg-type :create-card)
        (let [[_ board-slug list-id name] msg]
          (when-let [card (boards/create-card database list-id name)]
            (broadcast board-slug [:card-created board-slug card])))

        (= msg-type :update-card-location)
        (let [[_ board-slug card-id list-id position] msg]
          (when-let [card (boards/move-card database card-id list-id position)]
            (let [board (boards/get-board database board-slug)]
              (broadcast board-slug [:card-moved board-slug board]))))

        (= msg-type :edit-list)
        (let [[_ board-slug list-id name] msg]
          (when-let [list (boards/edit-list database list-id name)]
            (let [board (boards/get-board database board-slug)]
              (broadcast board-slug [:list-edited board-slug board]))))

        (= msg-type :update-card)
        (let [[_ board-slug card-id name description] msg]
          (when-let [card (boards/edit-card database card-id name description)]
            (let [board (boards/get-board database board-slug)]
              (broadcast board-slug [:card-edited board-slug board]))))

        (= msg-type :add-comment)
        (let [[_ board-slug card-id comment] msg
              user-id (:id user)]
          (when-let [comment (boards/add-comment database card-id user-id comment)]
            (let [board (boards/get-board database board-slug)]
              (broadcast board-slug [:comment-added board-slug board]))))

        (= msg-type :toggle-tag)
        (let [[_ board-slug card-id tag-id enable] msg
              user-id (:id user)]
          (when-let [comment (boards/toggle-tag database card-id tag-id enable)]
            (let [board (boards/get-board database board-slug)]
              (broadcast board-slug [:tag-toggled board-slug board]))))

        (= msg-type :toggle-card-member)
        (let [[_ board-slug card-id user-id enable] msg]
          (when-let [comment (boards/toggle-card-member database card-id user-id enable)]
            (let [board (boards/get-board database board-slug)]
              (broadcast board-slug [:card-member-toggled board-slug board]))))

        ))))
