(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('QuerySrv', function($http) {
            this.query = function(version, operations, config) {
                return $http.post('./api/' + version + '/query', {
                    query: operations
                }, config || {});
            };
        });
})();
