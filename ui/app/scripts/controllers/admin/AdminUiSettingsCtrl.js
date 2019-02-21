(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminUiSettingsCtrl', function($scope, $q, NotificationSrv, UiSettingsSrv, uiConfig) {
            var self = this;

            self.isDirtySetting = function(key, newValue) {
                var currentValue = (self.currentSettings[key] || {}).value;

                return newValue !== currentValue;
            };

            self.save = function(/*form*/) {
                var promises = [];

                self.settingsKeys.forEach(function(key) {
                    if(self.isDirtySetting(key, self.configs[key])) {
                        if(!self.currentSettings[key]) {
                            promises.push(UiSettingsSrv.create(key, self.configs[key]));
                        } else {
                            promises.push(UiSettingsSrv.update(self.currentSettings[key].id, key, self.configs[key]));
                        }
                    }
                });

                if(promises.length === 0) {
                    return;
                }

                $q.all(promises)
                    .then(function(/*responses*/) {
                        self.loadSettings();
                        NotificationSrv.log('UI Settings updated successfully', 'success');
                    })
                    .catch(function(/*errors*/) {
                        NotificationSrv.error('An error occurred during UI Settings update');
                    });
            };

            self.loadSettings = function(configurations) {
                var notifyRoot = false;
                var promise;

                if(configurations) {
                    promise = $q.resolve(configurations);
                } else {
                    promise = UiSettingsSrv.all(true);
                    notifyRoot = true;
                }

                promise.then(function(configs) {
                    self.settingsKeys = UiSettingsSrv.keys;
                    self.currentSettings = configs;

                    self.configs = {};
                    self.settingsKeys.forEach(function(key) {
                        self.configs[key] = (configs[key] || {}).value;
                    });

                    if(notifyRoot) {
                        $scope.$emit('ui-settings:refresh', configs);
                    }
                });
            };

            self.loadSettings(uiConfig);

        });
})();
