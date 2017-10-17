(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('dashboardItem', function($uibModal, $timeout, $q) {
            return {
                restrict: 'E',
                replace: true,
                scope: {
                    rowIndex: '=',
                    colIndex: '=',
                    component: '=',
                    metadata: '=',
                    autoload: '=',
                    refreshOn: '@',
                    mode: '@',
                    showEdit: '=',
                    onRemove: '&'
                },
                templateUrl: 'views/directives/dashboard/item.html',
                link: function(scope, element) {

                    scope.skipFields = function(fields, types) {
                        return _.filter(fields, function(item) {
                            return types.indexOf(item.type) === -1;
                        })
                    }

                    // scope.pickFields = function(fields, types) {
                    //     return _.filter()
                    // }

                    scope.editDashboard = function() {
                        var modalInstance = $uibModal.open({
                            scope: scope,
                            controller: function($scope, $uibModalInstance) {
                                $scope.cancel = function() {
                                    $uibModalInstance.dismiss();
                                };

                                $scope.save = function() {
                                    // TODO clear invalid filters


                                    $uibModalInstance.close($scope.component.options);
                                };
                            },
                            templateUrl: 'views/directives/dashboard/edit.dialog.html',
                            size: 'lg'
                        });

                        modalInstance.result.then(function(definition) {
                            scope.component.options = definition;

                            $timeout(function() {
                                scope.$broadcast(scope.refreshOn);
                            }, 500)

                        });
                    };

                    scope.editorFor = function(filter) {
                        if(filter.type === null) {
                            return;
                        }
                        var field = scope.metadata[scope.component.options.entity][filter.field];
                        var type = field.type;

                        if ((type=== 'string' || type === 'number') && field.values.length > 0) {
                            return 'enumeration';
                        }

                        return filter.type;
                    }

                    scope.promiseFor = function(filter, query) {
                        var field = scope.metadata[scope.component.options.entity][filter.field];

                        var promise = null;

                        if(field.labels.length > 0) {
                            promise = $q.resolve(_.map(field.labels, function(item){
                                return {text: item};
                            }));
                        } else if(field.values.length > 0) {
                            promise = $q.resolve(_.map(field.values, function(item){
                                return {text: item};
                            }));
                        } else {
                            promise = $q.resolve([]);
                        }

                        return promise.then(function(response) {
                            var list = [];

                            list = _.filter(response, function(item) {
                                var regex = new RegExp(query, 'gi');
                                return regex.test(item.text);
                            });

                            return $q.resolve(list);
                        });
                    }

                    scope.addFilter = function() {
                        console.log('addFilter');
                        scope.component.options.filters = scope.component.options.filters || [];

                        scope.component.options.filters.push({
                            field: null,
                            type: null
                        })
                    };

                    scope.removeFilter = function(index) {
                        scope.component.options.filters.splice(index, 1);
                    };

                    scope.setFilterField = function(filter) {
                        var entity = scope.component.options.entity;
                        var field = scope.metadata[entity][filter.field];

                        filter.type = field.type;

                        if(field.type === 'date') {
                            filter.value = {
                                from: undefined,
                                to: undefined
                            };
                        } else {
                            filter.value = null;
                        }

                    }
                }
            };
        });
})();
