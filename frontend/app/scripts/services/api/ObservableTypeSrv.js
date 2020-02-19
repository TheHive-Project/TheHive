(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('ObservableTypeSrv', function($http) {
            var self = this;
            var baseUrl = './api/observable/type';

            this.list = function() {
                return $http.get(baseUrl, {
                    params: {
                        range: 'all'
                    }
                });
            };

            this.get = function(name) {
                return $http.get(baseUrl + '/' + name);
            };

            this.map = function() {
                return self.list()
                    .then(function(response) {
                        return _.indexBy(response.data, '_id');
                    });
            };

            this.create = function(type) {
                return $http.post(baseUrl, type);
            };

            this.update = function(id, type) {
                return $http.patch(baseUrl + '/' + id, type);
            };

            this.remove = function(id) {
                return $http.delete(baseUrl + '/' + id);
            };
        });

})();
