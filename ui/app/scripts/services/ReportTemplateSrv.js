(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('ReportTemplateSrv', function($resource, $http) {
            var baseUrl = '/api/connector/cortex/report/template'; 
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
                }
            }

        });
})();
