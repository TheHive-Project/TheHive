(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminUiSettingsCtrl', function(UiSettingsSrv, uiConfig) {
            var self = this;

            self.save = function(/*form*/) {
                self.settingsKeys.forEach(function(key) {
                    //if(form[key].$dirty) {
                    if(!self.currentSettings[key]) {
                        UiSettingsSrv.create(key, self.configs[key]);
                    } else {
                        UiSettingsSrv.update(self.currentSettings[key].id, key, self.configs[key]);
                    }
                    //}
                });
            };

            self.loadSettings = function() {
                self.settingsKeys = UiSettingsSrv.keys;
                self.currentSettings = uiConfig;

                self.configs = {};
                self.settingsKeys.forEach(function(key) {
                    self.configs[key] = (uiConfig[key] || {}).value;
                });
            };

            self.loadSettings();

        });
})();
