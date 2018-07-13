(function() {
    'use strict';
    angular.module('theHiveServices').service('CaseReportSrv', function($q, $http) {
        this.list = function() {
                    var defer = $q.defer();
                    $http.get('./api/case_report')
                    .then(function(response) {
                        defer.resolve(response.data);
                    }, function(err) {
                        defer.reject(err);
                    });
                    return defer.promise;
        };

        this.create = function(template) {
            return $http.post('./api/case_report/create', template);
        }

        this.update = function(id, template) {
            return $http.patch('./api/case_report/' + id, template);
        }
    });
})();
