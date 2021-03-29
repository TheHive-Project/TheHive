(function () {
    'use strict';
    angular.module('theHiveServices')
        .service('TagSrv', function (QuerySrv, $q, VersionSrv, $http) {

            var self = this;

            this.tagsDefaultColour = '#000000';

            this.getFreeTags = function () {
                var defer = $q.defer();

                VersionSrv.get()
                    .then(function (appConfig) {
                        var defaultColour = appConfig.config.freeTagDefaultColour;

                        self.tagsDefaultColour = defaultColour;

                        return QuerySrv.query('v1', [
                            { _name: 'listTag' },
                            { _name: 'freetags' },
                            { _name: 'filter', _not: { colour: defaultColour } }
                        ], {
                            params: {
                                name: 'list-tags'
                            }
                        })
                    })

                    .then(function (response) {
                        defer.resolve(response.data);
                    });

                return defer.promise;
            };

            this.updateTag = function (id, patch) {
                return $http.patch('./api/v1/tag/' + id, patch);
            }

            this.removeTag = function (id) {
                return $http.delete('./api/v1/tag/' + id);
            }

            this.autoComplete = function (term) {
                var defer = $q.defer();

                var operations = [
                    { _name: 'tagAutoComplete', freeTag: term, limit: 20 }
                ]

                QuerySrv.call('v1', operations, {
                    name: 'tags-auto-complete'
                }).then(function (response) {
                    defer.resolve(_.map(response, function (tag) {
                        return { text: tag };
                    }));
                });

                return defer.promise;
            };

        });
})();
