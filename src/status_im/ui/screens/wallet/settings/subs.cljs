(ns status-im.ui.screens.wallet.settings.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :wallet/settings
 :<- [:wallet]
 (fn [{:keys [settings]}]
   settings))
