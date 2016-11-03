/**
 * Controller for new case modal page
 */
(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseCreationCtrl',
        function($rootScope, $scope, $state, $modalInstance, CaseSrv, AlertSrv, MetricsCacheSrv, template) {

            $rootScope.title = 'New case';
            $scope.activeTlp = 'active';
            $scope.active = true;
            $scope.pendingAsync = false;
            $scope.metricsCache = {};
            $scope.temp = {
                titleSuffix: '',
                task: ''
            };
            $scope.template = template;
            $scope.fromTemplate = angular.isDefined(template) && !_.isEqual($scope.template, {});

            if ($scope.fromTemplate === true) {

                MetricsCacheSrv.all().then(function(list) {
                    // Set basic info from template
                    $scope.newCase = {
                        status: 'Open',
                        title: '',
                        description: template.description,
                        tlp: template.tlp,
                        severity: template.severity
                    };

                    // Set metrics from template
                    $scope.metricsCache = list;
                    var metrics = {};
                    _.each(template.metricNames, function(m) {
                        metrics[m] = null;
                    });
                    $scope.newCase.metrics = metrics;

                    // Set tags from template
                    $scope.tags = template.tags;

                    // Set tasks from template
                    $scope.tasks = _.map(template.tasks, function(t) {
                        return t.title;
                    });
                });

            } else {
                $scope.tasks = [];
                $scope.newCase = {
                    status: 'Open'
                };
            }

            $scope.updateTlp = function(tlp) {
                $scope.newCase.tlp = tlp;
            };

            $scope.createNewCase = function(isValid) {
                if (!isValid) {
                    return;
                }

                $scope.newCase.tags = [];
                angular.forEach($scope.tags, function(tag) {
                    $scope.newCase.tags.push(tag.text);
                });
                $scope.newCase.tags = $.unique($scope.newCase.tags.sort());

                // Append title prefix
                if ($scope.fromTemplate) {
                    $scope.newCase.title = $scope.template.titlePrefix + ' ' + $scope.temp.titleSuffix;
                }

                $scope.newCase.tasks = _.map($scope.tasks, function(task) {
                    return {
                        title: task,
                        flag: false,
                        status: 'Waiting'
                    };
                });

                $scope.pendingAsync = true;
                CaseSrv.save({}, $scope.newCase, function(data) {
                    $state.go('app.case.details', {
                        caseId: data.id
                    });
                    $modalInstance.close();
                }, function(response) {
                    $scope.pendingAsync = false;
                    AlertSrv.error('CaseCreationCtrl', response.data, response.status);
                });
            };

            $scope.addTask = function(task) {
                if ($scope.tasks.indexOf(task) === -1) {
                    $scope.tasks.push(task);
                }
                $scope.temp.task = '';
                angular.element('.task-input').focus();
            };

            $scope.removeTask = function(task) {
                $scope.tasks = _.without($scope.tasks, task);
            };

            $scope.cancel = function() {
                $modalInstance.dismiss();
            };
        }
    );
})();
