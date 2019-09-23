(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('ProfileSrv', function($http) {
            var self = this;
            var baseUrl = './api/profile';

            this.list = function() {
                return $http.get(baseUrl);
            };

            this.get = function(name) {
                return $http.get(baseUrl + '/' + name);
            };

            this.map = function() {
                return self.list()
                    .then(function(response) {
                        return _.indexBy(response.data, 'name');
                    });
            };

        });

})();
