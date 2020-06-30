(function() {
    'use strict';

    angular.module('theHiveDirectives')
        .directive('flow', function(AuditSrv, AnalyzerInfoSrv, UserSrv) {
            return {
                restrict: 'E',
                templateUrl: 'views/directives/flow/flow.html',
                controller: function($scope, $window) {
                    this.$onInit = function() {
                        var rootId = '';
                        if (angular.isString($scope.root)) {
                            rootId = $scope.root;
                        } else {
                            rootId = 'any';
                        }

                        this.values = AuditSrv(rootId, parseInt($scope.max), $scope);
                    }

                    if ($window.opener) {
                        $scope.targetWindow = $window.opener;
                    }
                },
                controllerAs: '$flow',
                scope: {
                    'root': '@?',
                    'max': '@?'
                },
                link: function(scope) {
                    scope.getAnalyzerInfo = AnalyzerInfoSrv;
                    scope.getUserInfo = UserSrv.getCache;
                },
            };
        });
})();
