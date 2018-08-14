(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('responderActions', function(UtilsSrv) {
        return {
            restrict: 'E',
            replace: true,
            scope: {
                actions: '=',
                header: '@'
            },
            templateUrl: 'views/directives/responder-actions.html',
            link: function(scope, el) {

            },
            controller: function($scope, $uibModal) {
                $scope.showResponderJob = function(action) {
                    $uibModal.open({
                        scope: $scope,
                        templateUrl: 'views/partials/cortex/responder-action-dialog.html',
                        controller: 'ResponderActionDialogCtrl',
                        controllerAs: '$dialog',
                        size: 'max',
                        resolve: {
                            action: function() {
                                return action;
                            }
                        }
                    });
                };

                $scope.close = function() {
                    $uibModalInstance.dismiss();
                }
            }
        };
    });
})();
