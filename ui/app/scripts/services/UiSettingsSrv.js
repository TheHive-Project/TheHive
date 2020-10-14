(function() {
    'use strict';
    angular.module('theHiveServices').factory('UiSettingsSrv', function(ListSrv, $q) {

        var settings = null;

        var keys = [
            'hideEmptyCaseButton',
            'useAndFiltering'
        ];

        var factory = {
            keys: keys,
            clearCache: function() {
                settings = null;
            },

            get: function(name) {
                return settings[name];
            },

            create: function(name, value) {
                return ListSrv.save({listId: 'ui_settings'}, {
                    value: {
                        name: name,
                        value: value
                    }
                }).$promise;
            },

            update: function(id, name, value) {
                return ListSrv.update({itemId: id}, {
                    value: {
                        name: name,
                        value: value
                    }
                }).$promise;
            },

            all: function(force) {
                var deferred = $q.defer();

                if(settings === null || force) {
                    ListSrv.query({listId: 'ui_settings'}, {}, function(response) {
                        var json = response.toJSON();

                        settings = {};

                        _.each(_.keys(json), function(key) {
                            var setting = json[key];

                            settings[setting.name] = setting;
                            settings[setting.name].id = key;
                        });

                        deferred.resolve(settings);
                    }, function(response) {
                        deferred.reject(response);
                    });
                } else {
                    deferred.resolve(settings);
                }

                return deferred.promise;
            }
        };

        keys.forEach(function(key) {
            factory[key] = function() {
                return (settings[key] || {}).value;
            };
        });

        return factory;
    });
})();
