/**
 * Controller for new case modal page
 */
(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseCreationCtrl',
        function ($rootScope, $scope, $uibModalInstance, CaseSrv, TaxonomyCacheSrv, NotificationSrv, TagSrv, template) {

            $rootScope.title = 'New case';
            $scope.activeTlp = 'active';
            $scope.activePap = 'active';
            $scope.active = true;
            $scope.pendingAsync = false;
            $scope.temp = {
                titleSuffix: '',
                task: ''
            };
            $scope.template = template;
            $scope.fromTemplate = angular.isDefined(template) && !_.isEqual($scope.template, {});

            $scope.tags = [];

            if ($scope.fromTemplate === true) {

                // Set basic info from template
                $scope.newCase = _.defaults({
                    status: 'Open',
                    title: '',
                    description: template.description,
                    tlp: template.tlp,
                    pap: template.pap,
                    severity: template.severity
                }, {tlp: 2, pap: 2});

                // Set tags from template
                $scope.tags = template.tags;

                // Set tasks from template
                $scope.tasks = _.map(template.tasks, function (t) {
                    return t.title;
                });

            } else {
                $scope.tasks = [];
                $scope.newCase = {
                    status: 'Open'
                };
            }

            $scope.updateTlp = function (tlp) {
                $scope.newCase.tlp = tlp;
            };

            $scope.updatePap = function (pap) {
                $scope.newCase.pap = pap;
            };

            $scope.createNewCase = function (isValid) {
                if (!isValid) {
                    return;
                }

                $scope.newCase.tags = [];
                angular.forEach($scope.tags, function (tag) {
                    $scope.newCase.tags.push(tag.text);
                });
                $scope.newCase.tags = $.unique($scope.newCase.tags.sort());

                // Append title prefix
                if ($scope.fromTemplate) {
                    $scope.newCase.template = $scope.template.name;
                } else {
                    $scope.newCase.tasks = _.map($scope.tasks, function (task) {
                        return {
                            title: task,
                            flag: false,
                            status: 'Waiting'
                        };
                    });
                }

                $scope.pendingAsync = true;
                CaseSrv.save({}, $scope.newCase, function (data) {
                    $uibModalInstance.close(data);
                }, function (response) {
                    $scope.pendingAsync = false;
                    NotificationSrv.error('CaseCreationCtrl', response.data, response.status);
                });
            };

            $scope.fromTagLibrary = function() {
                TaxonomyCacheSrv.openTagLibrary()
                    .then(function(tags){
                        $scope.tags = $scope.tags.concat(tags);
                    })
            };

            $scope.addTask = function (task) {
                if ($scope.tasks.indexOf(task) === -1) {
                    $scope.tasks.push(task);
                }
                $scope.temp.task = '';
                angular.element('.task-input').focus();
            };

            $scope.removeTask = function (task) {
                $scope.tasks = _.without($scope.tasks, task);
            };

            $scope.cancel = function () {
                $uibModalInstance.dismiss();
            };

            $scope.getTags = function(query) {
                return TagSrv.autoComplete(query);
            };

            $scope.keys = function(o) {
                return _.keys(o).length;
            };
        }
    );
})();
