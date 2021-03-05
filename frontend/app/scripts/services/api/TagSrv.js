(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('TagSrv', function(QuerySrv, $q, $http) {

            this.getFreeTags = function() {
                var defer = $q.defer();

                var operations = [
                    { _name: 'listTag'},
                    { _name: 'freetags'},
                ]

                QuerySrv.query('v1', operations, {
                    params: {
                        name: 'list-tags'
                    }
                }).then(function(response) {
                    defer.resolve(response.data);
                });

                return defer.promise;
            };

            this.updateTag = function(id, patch) {
                return $http.patch('./api/v1/tag/' + id, patch);
            }

            this.autoComplete = function(term) {
                var defer = $q.defer();

                var operations = [
                    { _name: 'tagAutoComplete', freeTag: term, limit: 20}
                ]

                QuerySrv.call('v1', operations, {
                    name: 'tags-auto-complete'
                }).then(function(response) {
                    defer.resolve(_.map(response, function(tag) {
                        return {text: tag};
                    }));
                });

                return defer.promise;
            };

        });
})();
