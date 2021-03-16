(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('PlatformSrv', function($http) {

            this.getIndexStatus = function() {
                return $http.get('./api/v1/admin/index/status')
            }

            this.runReindex = function(indexName) {
                return $http.get('./api/v1/admin/index/'+indexName+'/reindex');
            }

            this.getCheckStats = function() {
                return $http.get('./api/v1/admin/check/stats')
            }

            this.runCheck = function(checkName) {
                return $http.get('./api/v1/admin/check/'+checkName+'/trigger');
            }

        });
})();
