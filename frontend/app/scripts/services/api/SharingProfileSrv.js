(function () {
    'use strict';
    angular.module('theHiveServices')
        .service('SharingProfileSrv', function ($http, $q) {
            var self = this;

            this.SHARING_RULES = {
                keys: [
                    'manual',
                    'existingOnly',
                    'upcomingOnly',
                    'all'
                ],
                values: {
                    manual: 'Items are not shared automatically but manually (default).',
                    existingOnly: 'Share all items when applied. New item won\'t be shared automatically.',
                    upcomingOnly: 'Only new items are shared.',
                    all: 'All items are shared, including new ones.'
                }
            }

            this.cache = null;

            this.list = function () {
                return $http.get('./api/v1/sharingProfile');
            };

            this.clearCache = function () {
                self.cache = null;
            };

            this.getCache = function (name) {
                return self.cache[name];
            };

            this.all = function () {
                var deferred = $q.defer();

                if (self.cache === null) {
                    $http.get('./api/v1/sharingProfile')
                        .then(function (response) {
                            self.cache = {};

                            _.each(response.data, function (profile) {
                                self.cache[profile.name] = profile;
                            });

                            deferred.resolve(self.cache);
                        });
                } else {
                    deferred.resolve(self.cache);
                }

                return deferred.promise;
            };

        });
})();
