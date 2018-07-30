(function() {
    'use strict';

    angular.module('theHiveControllers').controller('CaseDetailsCtrl', function($q, $scope, $state, $uibModal, CaseTabsSrv, UserInfoSrv, PSearchSrv, StatSrv, TagSrv) {

        CaseTabsSrv.activateTab($state.current.data.tab);

        $scope.isDefined = false;
        $scope.state = {
            'editing': false,
            'isCollapsed': true
        };

        var getPromiseFor = function(objectType) {
            return StatSrv.getPromise({
                objectType: objectType,
                field: 'tags',
                limit: 1000
            });
        };

        $scope.autoComplete = function(term) {
            var defer = $q.defer();

            getPromiseFor('case').then(function(response) {
                defer.resolve(mapTags(response.data, term) || []);
            });

            return defer.promise;
        };

        var mapTags = function(collection, term) {
            return _.map(_.filter(_.keys(collection), function(tag) {
                console.log(term);
                var regex = new RegExp(term, 'gi');
                return regex.test(tag);
            }), function(tag) {
                return {text: tag};
            });
        };

        $scope.attachments = PSearchSrv($scope.caseId, 'case_task_log', {
            scope: $scope,
            filter: {
                '_and': [
                    {
                        '_not': {
                            'status': 'Deleted'
                        }
                    }, {
                        '_contains': 'attachment.id'
                    }, {
                        '_parent': {
                            '_type': 'case_task',
                            '_query': {
                                '_parent': {
                                    '_type': 'case',
                                    '_query': {
                                        '_id': $scope.caseId
                                    }
                                }
                            }
                        }
                    }
                ]
            },
            pageSize: 100,
            nparent: 1
        });

        $scope.hasNoMetrics = function(caze) {
            return !caze.metrics || _.keys(caze.metrics).length === 0 || caze.metrics.length === 0;
        };

        $scope.addMetric = function(metric) {
            var modalInstance = $uibModal.open({
                scope: $scope,
                templateUrl: 'views/partials/case/case.add.metric.html',
                controller: 'CaseAddMetadataConfirmCtrl',
                size: '',
                resolve: {
                    data: function() {
                        return metric;
                    }
                }
            });

            modalInstance.result.then(function() {
                if (!$scope.caze.metrics) {
                    $scope.caze.metrics = {};
                }
                $scope.caze.metrics[metric.name] = null;
                $scope.updateField('metrics', $scope.caze.metrics);
                $scope.updateMetricsList();
            });
        };

        $scope.openAttachment = function(attachment) {
            $state.go('app.case.tasks-item', {
                caseId: $scope.caze.id,
                itemId: attachment.case_task.id
            });
        };
    });

    angular.module('theHiveControllers').controller('CaseCustomFieldsCtrl', function($scope, $uibModal, CustomFieldsCacheSrv) {
        var getTemplateCustomFields = function(customFields) {
            var result = [];

            result = _.pluck(_.sortBy(_.map(customFields, function(definition, name){
                return {
                    name: name,
                    order: definition.order
                }
            }), function(item){
                return item.order;
            }), 'name');

            return result;
        }

        $scope.getCustomFieldName = function(fieldDef) {
            return 'customFields.' + fieldDef.reference + '.' + fieldDef.type;
        };

        $scope.addCustomField = function(customField) {
            var modalInstance = $uibModal.open({
                scope: $scope,
                templateUrl: 'views/partials/case/case.add.field.html',
                controller: 'CaseAddMetadataConfirmCtrl',
                size: '',
                resolve: {
                    data: function() {
                        return customField;
                    }
                }
            });

            modalInstance.result.then(function() {
                var temp = $scope.caze.customFields || {};

                var customFieldValue = {};
                customFieldValue[customField.type] = null;
                customFieldValue.order = _.keys(temp).length + 1;

                $scope.updateField('customFields.' + customField.reference, customFieldValue);
                $scope.updateCustomFieldsList();

                $scope.caze.customFields[customField.reference] = customFieldValue;
            });
        };

        $scope.updateCustomFieldsList = function() {
            CustomFieldsCacheSrv.all().then(function(fields) {
                $scope.orderedFields = getTemplateCustomFields($scope.caze.customFields);
                $scope.allCustomFields = _.omit(fields, _.keys($scope.caze.customFields));
                $scope.customFieldsAvailable = _.keys($scope.allCustomFields).length > 0;
            });
        };

        $scope.updateCustomFieldsList();

        $scope.$on('case:refresh-custom-fields', function() {            
            $scope.updateCustomFieldsList();
        });
    });

    angular.module('theHiveControllers').controller('CaseAddMetadataConfirmCtrl', function($scope, $uibModalInstance, data) {
        $scope.data = data;

        $scope.cancel = function() {
            $uibModalInstance.dismiss(data);
        };

        $scope.confirm = function() {
            $uibModalInstance.close(data);
        };
    });

})();
