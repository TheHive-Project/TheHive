(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('responderActions', function() {
        return {
            restrict: 'E',
            replace: true,
            scope: {
                actions: '=',
                header: '@'
            },
            templateUrl: 'views/directives/responder-actions.html',
            controller: function($scope, $uibModal) {
                $scope.$watchCollection('actions.values', function(list) {
                    if(!list) {
                        return;
                    }

                    _.each(_.isArray(list) ? list : list.values, function(action) {
                        if (action.status === 'Failure') {
                            action.errorMessage = (JSON.parse(action.report) || {}).errorMessage;
                        }
                    });
                });

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
            }
        };
    });
})();
