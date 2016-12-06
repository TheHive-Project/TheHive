(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('AnalyzerSrv', function($resource) {
            return $resource('/api/connector/cortex/analyzer/:analyzerId', {}, {
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
