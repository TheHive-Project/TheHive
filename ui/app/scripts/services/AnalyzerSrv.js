(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('AnalyzerSrv', function($resource) {
            return $resource('/api/analyzer/:analyzerId', {}, {
                query: {
                    method: 'POST',
                    url: '/api/analyzer/_search',
                    isArray: true
                },
                update: {
                    method: 'PATCH'
                }
            });
        });
})();
