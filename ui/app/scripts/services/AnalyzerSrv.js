(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('AnalyzerSrv', function($resource) {
            return $resource('/api/analyzer/:analyzerId', {}, {
                query: {
                    method: 'GET',
                    url: '/api/connector/cortex/analyzer',
                    isArray: true
                },
                update: {
                    method: 'PATCH'
                }
            });
        });
})();
