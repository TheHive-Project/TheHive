
(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('AttackPatternSrv', function($http, $q, QuerySrv) {
            var baseUrl = './api/v1/pattern';

            this.list = function() {
                return QuerySrv.call('v1', [
                    { _name: 'listPattern' }
                ], {
                    name:'list-attack-patterns'
                });
            };

            this.get = function(id) {

                var defer = $q.defer();

                QuerySrv.call('v1', [{
                    '_name': 'getPattern',
                    'idOrName': id
                }], {
                    name:'get-attach-pattern-' + id,
                    page: {
                        from: 0,
                        to: 1,
                        extraData: [
                            "parent",
                            "children"
                        ]
                    }
                }).then(function(response) {
                    defer.resolve(response[0]);
                }).catch(function(err){
                    defer.reject(err);
                });

                return defer.promise;                
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
