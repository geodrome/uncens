(ns uncens.models.user
  (:use [uncens.models.db]
        [korma core]
        [noir.util.test])
  (:require [noir.validation :as vali]
            [noir.util.crypt :as crypt]
            [noir.session :as session]
            [noir.cookies :as cookies]
            [crypto.random :as crand]))

;; Gets

(defn get-username
  [u]
  (first
    (select user
      (where {:username_lower (clojure.string/lower-case u)}))))

;; Validation

(defn valid-username?
  [u]
  (re-find #"^[a-zA-Z][\w| |-]*$" u))

(defn invalid-username? [u] (not (valid-username? u)))

(defn username-taken?
  [u]
  (first (select user (where {:username_lower (clojure.string/lower-case u)}))))

(defn username-available? [u] (not (username-taken? u)))

(defn email-taken?
  [email]
  (first (select user (where {:email email}))))

(defn email-available? [email] (not (email-taken? email)))

(def username-restrictions
  "Your username must start with a letter.
   The rest of the characters may be letters, numbers, spaces, dashes, or underscores.")

(defn valid? [{:keys [username pass pass2 spam] :as usr}]
  (vali/rule (username-available? username)
    [:username (str "Username '" username "' is taken.")])
  (vali/rule (valid-username? username)
    [:username username-restrictions])
  ;(vali/rule (vali/is-email? email)
  ;  [:email "You must provide a valid email address."])
  ;(vali/rule (email-available? email)
  ;  [:email "There is already a user registered with this email address."])
  (vali/rule (vali/min-length? pass 4)
    [:pass "Password must be at least 4 characters long."])
  (vali/rule (= pass pass2)
    [:pass "Passwords do not match."])
  (vali/rule (= (clojure.string/lower-case spam) "blue")
    [:spam "Please answer this question to prove that you're not a robot."])
  ;; flash-put errors for redirecting to anchor when posting comments
  ;; and registering in one step
  ;; NOTE: the last error for each key will be the one displayed
  ;; (as opposed to the first error when errors are read via vali/on-error)
  (when (invalid-username? username)
    (session/flash-put! :username-error username-restrictions))
  (when (username-taken? username)
    (session/flash-put! :username-error (str "Username '" username "' is taken.")))
  ;(when (email-taken? email)
  ;  (session/flash-put! :email-error "There is already a user registered with this email address."))
  ;(when (not (vali/is-email? email))
  ;  (session/flash-put! :email-error "You must provide a valid email address."))
  (when (not= pass pass2)
    (session/flash-put! :password-error "Passwords do not match."))
  (when (not (vali/min-length? pass 4))
    (session/flash-put! :password-error "Password must be at least 4 characters long."))
  (when (not= (clojure.string/lower-case spam) "blue")
    (session/flash-put! :spam-error "Please answer this question to prove that you're not a robot."))
  (not (vali/errors? :username :pass :spam)))

;; Checks

(defn check-login-cookie
  []
  (when-let [cookie (cookies/get :login)]
    (let [[user token] (clojure.string/split cookie #"-")]
      (when-let [id (:id (first
                           (select logn
                             (where {:username user :token token}))))]
        (session/put! :username user)
        (session/put! :userid id)
        true))))

(defn logged-in? []
  (or (session/get :username)
    (check-login-cookie)))

;; Operations

(defn set-login-cookie!
  [username userid]
  (let [token (crand/hex 16)]
    (cookies/put! :login {:value (str username "-" token) :path "/" :max-age (* 30 24 60 60)})
    (insert logn
      (values {:id userid :username username :token token}))))

(defn add!
  [{:keys [username pass pass2] :as usr}]
  (when (valid? usr)
    (insert user
      (values {;:email (clojure.string/lower-case email)
               :username username
               :username_lower (clojure.string/lower-case username)
               :password (crypt/encrypt pass)}))))

(defn login!
  [{:keys [username pass] :as usr} remember?]
  (let [{stored-pass :password id :id} (get-username username)]
    (if (and stored-pass
             (crypt/compare pass stored-pass))
      (do
        (session/put! :username username)
        (session/put! :userid id)
        (when remember?
          (set-login-cookie! username id)))
      (do
        (session/flash-put! :error "Invalid username or password.")))))

(defn logout! []
  (delete logn (where {:username (session/get :username)}))
  (session/clear!))



