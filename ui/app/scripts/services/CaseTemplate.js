(function() {
    'use strict';
    angular.module('theHiveServices').service('CaseTemplateSrv', function($q, $http) {
        this.list = function() {
            var defer = $q.defer();
            $http.post('./api/case/template/_search', {}, {
                params: {
                    range: 'all'
                }
            }).then(function(response) {
                defer.resolve(response.data);
            }).catch(function(err) {
                defer.reject(err);
            });
            return defer.promise;
        };

        this.get = function(id) {
            var defer = $q.defer();
            $http.get('./api/case/template/' + id)
              .then(function(response) {
                  defer.resolve(response.data);
              }).catch(function(err) {
                  defer.reject(err);
              });
            return defer.promise;
        };

        this.delete = function(id) {
            return $http.delete('./api/case/template/' + id);
        };

        this.create = function(template) {
            return $http.post('./api/case/template', template);
        };

        this.update = function(id, template) {
            return $http.patch('./api/case/template/' + id, template);
        };
    });
})();
