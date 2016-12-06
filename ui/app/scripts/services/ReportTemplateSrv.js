(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('ReportTemplateSrv', function($resource, $http) {
            var baseUrl = '/api/report/template'; 
            var resource = $resource(baseUrl, {}, {
                query: {
                    method: 'POST',
                    url: baseUrl + '/_search',
                    isArray: true
                },
                update: {
                    method: 'PATCH'
                },
                create: {
                    method: 'POST'
                },
                save: {
                    method: 'PUT'
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
                        return $http.put(baseUrl + '/' + tpl.id, tpl, {});
                    } else {
                        return $http.post(baseUrl, tpl, {});
                    } 
                }
            }

        });
})();
