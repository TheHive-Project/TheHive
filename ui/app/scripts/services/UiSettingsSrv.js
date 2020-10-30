(function() {
    'use strict';
    angular.module('theHiveServices').service('UiSettingsSrv', function(ListSrv, $q) {

        var self = this;

        this.settings = null;
        this.keys = [
            'hideEmptyCaseButton',
            'useAndForCaseTagsFilter',
            'useAndForAlertTagsFilter'
        ];

        this.clearCache = function() {
            self.settings = null;
        };

        this.get = function(name) {
            return self.settings[name];
        };

        this.create = function(name, value) {
            return ListSrv.save({listId: 'ui_settings'}, {
                value: {
                    name: name,
                    value: value
                }
            }).$promise;
        };

        this.update = function(id, name, value) {
            return ListSrv.update({itemId: id}, {
                value: {
                    name: name,
                    value: value
                }
            }).$promise;
        };

        this.all = function(force) {
            var deferred = $q.defer();

            if(self.settings === null || force) {
                ListSrv.query({listId: 'ui_settings'}, {}, function(response) {
                    var json = response.toJSON();

                    self.settings = {};

                    _.each(_.keys(json), function(key) {
                        var setting = json[key];

                        self.settings[setting.name] = setting;
                        self.settings[setting.name].id = key;
                    });

                    deferred.resolve(self.settings);
                }, function(response) {
                    deferred.reject(response);
                });
            } else {
                deferred.resolve(self.settings);
            }

            return deferred.promise;
        };

        this.keys.forEach(function(key) {
            self[key] = function() {
                return ((self.settings|| {})[key] || {}).value;
            };
        });

    });
})();
