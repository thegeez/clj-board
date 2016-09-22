# clj-board

A drag & drop "kanban board" example with Clojure

Blog post: http://thegeez.net/2016/09/22/trello_clone_clone_in_clojure.html

Demo: https://clj-board.herokuapp.com

## Screenshots

### Board
![Board](/resources/screenshot/cljboard-board.png "Board")
### Card
![Card](/resources/screenshot/cljboard-card.png "Full card")
### Drag & drop
![Drag and Drop](/resources/screenshot/cljboard-drag.png "Drag and drop")

### Development
This uses an in-process/in-memory only database. In the `user` namespace, through `lein repl/cider` etc.:
```
   (all) ;; to start the component system and figwheel
   (reset) ;; to reset the whole component system
```
The site runs under http://localhost:8080.

### Running production uberjar (for heroku):
```
   lein uberjar
   java -jar target/clj-board-prod-standalone.jar PORT DB-URL
```
The app requires a PostgreSQL database add-on.
Run the database migrations from the repl:
```
    heroku run java -cp target/clj-board-prod-standalone.jar clojure.main
    => (require 'net.thegeez.clj-board.main)
    => (net.thegeez.clj-board.main/reset-db)
```

## About

Written by:
Gijs Stuurman / [@thegeez][twt] / [Blog][blog] / [GitHub][github]

[twt]: http://twitter.com/thegeez
[blog]: http://thegeez.net
[github]: https://github.com/thegeez


Copyright © 2016 Gijs Stuurman
