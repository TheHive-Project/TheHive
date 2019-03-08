(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('ReportTemplateSrv', function($resource, $http) {
            var baseUrl = './api/connector/cortex/report/template';
            var resource = $resource(baseUrl, {}, {
                query: {
                    method: 'POST',
                    url: baseUrl + '/_search',
                    isArray: true
                }
            });

            return {
                get: function() {
                    return resource;
                },

                list: function() {
                    return $http.post(baseUrl + '/_search', {
                        range: 'all'
                    });
                },

                save: function(tpl) {
                    if(tpl.id) {
                        return $http.patch(baseUrl + '/' + tpl.id, _.omit(tpl, 'id'), {});
                    } else {
                        return $http.post(baseUrl, tpl, {});
                    }
                },

                delete: function(tplId) {
                    return $http.delete(baseUrl + '/' + tplId);
                },

                import: function(post) {
                    var postData = {
                        templates: post.attachment
                    };

                    return $http({
                        method: 'POST',
                        url: baseUrl + '/_import',
                        headers: {
                            'Content-Type': undefined
                        },
                        transformRequest: function (data) {
                            var formData = new FormData(),
                                copy = angular.copy(data, {});
                                // _json = {};

                            angular.forEach(data, function (value, key) {
                                if (Object.getPrototypeOf(value) instanceof Blob || Object.getPrototypeOf(value) instanceof File) {
                                    formData.append(key, value);
                                    delete copy[key];
                                }
                            });

                            //formData.append("attributes", angular.toJson(_json));

                            return formData;
                        },
                        data: postData

                    });
                }
            };

        });
})();
