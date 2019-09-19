(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('ProfileSrv', function($http) {

            var baseUrl = './api/profile';

            var factory = {
                list: function() {
                    return $http.get(baseUrl);
                },
                get: function(name) {
                    return $http.get(baseUrl + '/' + name);
                }
            };

            return factory;
        });

})();
