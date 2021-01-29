
(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('AttackPatternSrv', function($http, QuerySrv) {
            var baseUrl = './api/v1/pattern';

            this.list = function() {
                return QuerySrv.call('v1', [
                    { _name: 'listPattern' }
                ], {
                    name:'list-attack-patterns'
                });
            };

            this.get = function(id) {
                return $http.get(baseUrl + '/' + id)
                    .then(function(response){
                        return response.data;
                    });
            };

            this.import = function(post) {
                var postData = {
                    file: post.attachment
                };

                return $http({
                    method: 'POST',
                    url: baseUrl + '/import/attack',
                    headers: {
                        'Content-Type': undefined
                    },
                    transformRequest: function (data) {
                        var formData = new FormData(),
                            copy = angular.copy(data, {});

                        angular.forEach(data, function (value, key) {
                            if (Object.getPrototypeOf(value) instanceof Blob || Object.getPrototypeOf(value) instanceof File) {
                                formData.append(key, value);
                                delete copy[key];
                            }
                        });

                        return formData;
                    },
                    data: postData
                });
            };
        });

})();
