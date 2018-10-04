(ns ^{:doc "Transit custom readers and writers, required when adding a new record implementing StatusMessage protocol"}
 status-im.transport.message.transit
  (:require [status-im.transport.message.v1.contact :as v1.contact]
            [status-im.transport.message.v1.protocol :as v1.protocol]
            [status-im.transport.message.v1.core :as v1]
            [status-im.constants :as constants]
            [cognitect.transit :as transit]))

;; When adding a new reccord implenting the StatusMessage protocol it is required to implement:
;; - a handler that will turn the clojure record into a javascript datastructure.
;; - a reader that will turn the javascript datastructure back into a clojure record.

;; Use the existing types as exemples of how this is done

;;
;; Writer handlers
;;

;; Each writer defines a tag and a representation
;; The tag will determine which reader is used to recreate the clojure record
;; When migrating a particular record, it is important to use a different type and still handle the previous
;; gracefully for compatibility
(deftype NewContactKeyHandler []
  Object
  (tag [this v] "c1")
  (rep [this {:keys [sym-key topic message]}]
    #js [sym-key topic message]))

(deftype ContactRequestHandler []
  Object
  (tag [this v] "c2")
  (rep [this {:keys [name profile-image address fcm-token]}]
    #js [name profile-image address fcm-token]))

(deftype ContactRequestConfirmedHandler []
  Object
  (tag [this v] "c3")
  (rep [this {:keys [name profile-image address fcm-token]}]
    #js [name profile-image address fcm-token]))

(deftype ContactUpdateHandler []
  Object
  (tag [this v] "c6")
  (rep [this {:keys [name profile-image address fcm-token]}]
    #js [name profile-image address fcm-token]))

;; It's necessary to support old clients understanding only older, needlesy complex and verbose command content
(defn- new->legacy-command-data [{:keys [command-path params] :as content}]
  (get {["send" #{:personal-chats}]    [{:command-ref ["transactor" :command 83 "send"]
                                         :command "send"
                                         :bot "transactor"
                                         :command-scope-bitmask 83}
                                        constants/content-type-command]
        ["request" #{:personal-chats}] [{:command-ref ["transactor" :command 83 "request"]
                                         :request-command-ref ["transactor" :command 83 "send"]
                                         :command "request"
                                         :request-command "send"
                                         :bot "transactor"
                                         :command-scope-bitmask 83
                                         :prefill [(get params :asset)
                                                   (get params :amount)]}
                                        constants/content-type-command-request]}
       command-path))

(deftype MessageHandler []
  Object
  (tag [this v] "c4")
  (rep [this {:keys [content content-type message-type clock-value timestamp]}]
    (condp = content-type
      constants/text-content-type ;; append new content add the end, still pass content the old way at the old index
      #js [(:text content) content-type message-type clock-value timestamp content]
      constants/content-type-command ;; handle command compatibility issues
      (let [[legacy-content legacy-content-type] (new->legacy-command-data content)]
        #js [(merge content legacy-content) (or legacy-content-type content-type) message-type clock-value timestamp])
      ;; no need for legacy conversions for rest of the content types
      #js [content content-type message-type clock-value timestamp])))

(deftype MessagesSeenHandler []
  Object
  (tag [this v] "c5")
  (rep [this {:keys [message-ids]}]
    (clj->js message-ids)))

(deftype GroupLeaveHandler []
  Object
  (tag [this v] "g3")
  (rep [this _]
    (clj->js nil)))

(deftype GroupMembershipUpdateHandler []
  Object
  (tag [this v] "g5")
  (rep [this {:keys [chat-id chat-name admin participants leaves version signature message]}]
    #js [chat-id chat-name admin participants leaves version signature message]))

(def writer (transit/writer :json
                            {:handlers
                             {v1.contact/NewContactKey           (NewContactKeyHandler.)
                              v1.contact/ContactRequest          (ContactRequestHandler.)
                              v1.contact/ContactRequestConfirmed (ContactRequestConfirmedHandler.)
                              v1.contact/ContactUpdate           (ContactUpdateHandler.)
                              v1.protocol/Message                (MessageHandler.)
                              v1.protocol/MessagesSeen           (MessagesSeenHandler.)
                              v1/GroupLeave                      (GroupLeaveHandler.)
                              v1/GroupMembershipUpdate           (GroupMembershipUpdateHandler.)}}))

;;
;; Reader handlers
;;

(def ^:private legacy-ref->new-path
  {["transactor" :command 83 "send"]    ["send" #{:personal-chats}]
   ["transactor" :command 83 "request"] ["request" #{:personal-chats}]})

(defn- legacy->new-command-content [{:keys [command-path command-ref] :as content}]
  (if command-path
    ;; `:command-path` set, message produced by newer app version, nothing to do
    content
    ;; we have to look up `:command-path` based on legacy `:command-ref` value and assoc it to content
    (assoc content :command-path (get legacy-ref->new-path command-ref))))

(defn- legacy->new-message-data [content content-type]
  ;; handling only the text content case
  (cond
    (= content-type constants/text-content-type)
    (if (and (map? content) (string? (:text content)))
      ;; correctly formatted map
      [content content-type]
      ;; create safe `{:text string-content}` value from anything else
      [{:text (str content)} content-type])
    (or (= content-type constants/content-type-command)
        (= content-type constants/content-type-command-request))
    [(legacy->new-command-content content) constants/content-type-command]
    :else
    [content content-type]))

;; Here we only need to call the record with the arguments parsed from the clojure datastructures
(def reader (transit/reader :json
                            {:handlers
                             {"c1" (fn [[sym-key topic message]]
                                     (v1.contact/NewContactKey. sym-key topic message))
                              "c2" (fn [[name profile-image address fcm-token]]
                                     (v1.contact/ContactRequest. name profile-image address fcm-token))
                              "c3" (fn [[name profile-image address fcm-token]]
                                     (v1.contact/ContactRequestConfirmed. name profile-image address fcm-token))
                              "c4" (fn [[legacy-content content-type message-type clock-value timestamp content]]
                                     (let [[new-content new-content-type] (legacy->new-message-data (or content legacy-content) content-type)]
                                       (v1.protocol/Message. new-content new-content-type message-type clock-value timestamp)))
                              "c7" (fn [[content content-type message-type clock-value timestamp]]
                                     (v1.protocol/Message. content content-type message-type clock-value timestamp))
                              "c5" (fn [message-ids]
                                     (v1.protocol/MessagesSeen. message-ids))
                              "c6" (fn [[name profile-image address fcm-token]]
                                     (v1.contact/ContactUpdate. name profile-image address fcm-token))
                              "g5" (fn [[chat-id chat-name admin participants leaves version signature message]]
                                     (v1/GroupMembershipUpdate. chat-id chat-name admin participants leaves version signature message))}})) ; removed group chat handlers for https://github.com/status-im/status-react/issues/4506

(defn serialize
  "Serializes a record implementing the StatusMessage protocol using the custom writers"
  [o]
  (transit/write writer o))

(defn deserialize
  "Deserializes a record implementing the StatusMessage protocol using the custom readers"
  [o]
  (try (transit/read reader o) (catch :default e nil)))
