(function() {
    'use strict';
    angular.module('theHiveServices').factory('AnalyzerInfoSrv', function(AnalyzerSrv) {
        var analyzerCache = {};
        return function(id) {
            if (angular.isDefined(analyzerCache[id])) {
                return analyzerCache[id];
            } else {
                analyzerCache[id] = AnalyzerSrv.get({
                    'analyzerId': id
                });
                return analyzerCache[id];
            }
        };
    });
})();
