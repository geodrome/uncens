(ns uncens.models.db
  (:use [uncens.config :only [config]]
        [korma db core])
  (:require [clojure.java.jdbc :as jdbc]))

(def db-spec (:db-spec config))

;; Tables

(defn make-table-page
  []
  (try
    (jdbc/with-connection db-spec
      (jdbc/create-table :page
        [:id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
        [:url "varchar(512)" "UNIQUE"]
        [:title "varchar(255)"]
        [:author "varchar(127)"]
        [:content :text]
        [:ext_method "varchar(20)"]
        [:published_date :datetime]
        [:uncensored_date :timestamp]))
    (catch Exception e (.toString e)))) ;; add exception handling everywhere?

(defn make-table-user
  []
  (jdbc/with-connection db-spec
    (jdbc/create-table :user
      [:id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
      [:email "varchar(127)"]
      [:username "varchar(32)"]
      [:username_lower "varchar(32)"]
      [:password "varchar(127)"])))

(defn make-table-comment
  []
  (jdbc/with-connection db-spec
    (jdbc/create-table :comment
      [:id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
      [:page_id :integer "REFERENCES page (id)"]
      [:user_id :integer "REFERENCES user (id)"]
      [:content :text]
      [:comment_date :timestamp])))

(defn make-table-login
  []
  (jdbc/with-connection db-spec
    (jdbc/create-table :login
      [:id :integer]
      [:username "varchar(32)"]
      [:token "varchar(32)"])))

(defn drop-tables
  []
  (jdbc/with-connection db-spec
    (jdbc/drop-table :page)
    (jdbc/drop-table :user)
    (jdbc/drop-table :comment)
    (jdbc/drop-table :login)))

;; Korma

(defdb korma-db (mysql db-spec))

(defentity page
  (pk :id))

(defentity user
  (pk :id))

(defentity comm
  (table :comment)
  (pk :id)
  (belongs-to user))

(defentity logn
  (table :login))





