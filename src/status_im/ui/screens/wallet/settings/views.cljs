(ns status-im.ui.screens.wallet.settings.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [re-frame.core :as re-frame]
            [pluto.reader.hooks :as hooks]
            [status-im.i18n :as i18n]
            [status-im.ui.components.list.views :as list]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.styles :as components.styles]
            [status-im.ui.components.toolbar.view :as toolbar]
            [status-im.ui.components.status-bar.view :as status-bar]
            [status-im.ui.screens.wallet.styles :as wallet.styles]
            [status-im.utils.ethereum.core :as ethereum]
            [status-im.utils.ethereum.tokens :as tokens]))

(def hook
  "Hook for extensions"
  {:properties
   {:title  :string}
   :hook
   (reify hooks/Hook
     (hook-in [_ id {:keys [title]} {:keys [db]}]
       (assoc-in db [:wallet :settings "Hello"] {:title title}))
     (unhook [_ id {:keys [scope]} {:keys [db] :as cofx}]))})

(defn- render-token [{:keys [symbol name icon]} visible-tokens]
  [list/list-item-with-checkbox
   {:checked?        (contains? visible-tokens (keyword symbol))
    :on-value-change #(re-frame/dispatch [:wallet.settings/toggle-visible-token (keyword symbol) %])}
   [list/item
    [list/item-image icon]
    [list/item-content
     [list/item-primary name]
     [list/item-secondary symbol]]]])

(defview manage-assets []
  (letsubs [network        [:network]
            visible-tokens [:wallet/visible-tokens-symbols]]
    [react/view (merge components.styles/flex {:background-color :white})
     [status-bar/status-bar {:type :modal-wallet}]
     [toolbar/toolbar {:style wallet.styles/toolbar}
      [toolbar/nav-text {:handler             #(do (re-frame/dispatch [:update-wallet])
                                                   (re-frame/dispatch [:navigate-back]))
                         :style               {:color :white}
                         :accessibility-label :done-button}
       (i18n/label :t/done)]
      [toolbar/content-title {:color :white}
       (i18n/label :t/wallet-assets)]]
     [react/view {:style components.styles/flex}
      [list/flat-list {:data      (tokens/sorted-tokens-for (ethereum/network->chain-keyword network))
                       :key-fn    (comp str :symbol)
                       :render-fn #(render-token % visible-tokens)}]]]))

(defview settings-hook []
  (letsubs [params        [:get-screen-params]]
    [react/view {:style (merge {:flex 0.5 :background-color :white :height 100})}
     (println "!!!" params)
     [status-bar/status-bar {:type :modal-wallet}]
     [toolbar/toolbar {:style wallet.styles/toolbar}
      [toolbar/nav-text {:handler             #(do (re-frame/dispatch [:update-wallet])
                                                   (re-frame/dispatch [:navigate-back]))
                         :style               {:color :white}
                         :accessibility-label :done-button}
       (i18n/label :t/done)]
      [toolbar/content-title {:color :white}
       "Salut"]]]))

(defview toolbar-view []
  (letsubs [settings [:wallet/settings]]
    [toolbar/toolbar {:style wallet.styles/toolbar :flat? true}
     nil
     [toolbar/content-wrapper]
     [toolbar/actions
      [{:icon      :icons/options
        :icon-opts {:color               :white
                    :accessibility-label :options-menu-button}
        :options   (into [{:label  (i18n/label :t/wallet-manage-assets)
                           :action #(re-frame/dispatch [:navigate-to :wallet-settings-assets])}
                          {:label  "Test"
                           :action #(re-frame/dispatch [:navigate-to :wallet-settings-hook {}])}]
                         settings)}]]]))
