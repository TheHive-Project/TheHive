(function() {
    'use strict';

    angular.module('theHiveDirectives')
        .directive('flow', function(AuditSrv, AnalyzerInfoSrv, UserSrv, $window) {
            return {
                restrict: 'E',
                templateUrl: 'views/directives/flow/flow.html',
                scope: {
                    'root': '@?',
                    'max': '@?'
                },
                link: function(scope) {
                    var rootId = '';
                    if (angular.isString(scope.root)) {
                        rootId = scope.root;
                    } else {
                        rootId = 'any';
                    }
                    scope.getAnalyzerInfo = AnalyzerInfoSrv;
                    scope.getUserInfo = UserSrv.getCache;
                    scope.values = AuditSrv(rootId, parseInt(scope.max), scope);

                    if ($window.opener) {
                        scope.targetWindow = $window.opener;
                    }
                },
            };
        });
})();
