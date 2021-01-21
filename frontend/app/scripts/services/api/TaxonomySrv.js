(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('TaxonomySrv', function($http, QuerySrv) {
            var baseUrl = './api/v1/taxonomy';

            this.list = function() {
                return QuerySrv.call('v1', [
                    { _name: 'listTaxonomy' }
                ], {
                    name:'list-taxonomies'
                });
            };

            this.get = function(name) {
                return $http.get(baseUrl + '/' + name);
            };

            this.toggleActive = function(id, active) {
                return $http.put([baseUrl, id, !!active ? 'activate' : 'deactivate'].join('/'));
            };

            this.create = function(profile) {
                return $http.post(baseUrl, profile);
            };

            this.update = function(id, profile) {
                return $http.patch(baseUrl + '/' + id, profile);
            };

            this.remove = function(id) {
                return $http.delete(baseUrl + '/' + id);
            };

            this.import = function(post) {
                var postData = {
                    file: post.attachment
                };

                return $http({
                    method: 'POST',
                    url: baseUrl + '/import-zip',
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
