(function() {
    'use strict';

    angular.module('theHiveControllers').controller('CaseDetailsCtrl', function($scope, $state, $uibModal, CaseTabsSrv, UserSrv, TagSrv, PSearchSrv) {

        CaseTabsSrv.activateTab($state.current.data.tab);

        $scope.isDefined = false;
        $scope.state = {
            'editing': false,
            'isCollapsed': true
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


        var connectors = $scope.appConfig.connectors;
        if(connectors.cortex && connectors.cortex.enabled) {
            $scope.actions = PSearchSrv(null, 'connector/cortex/action', {
                scope: $scope,
                streamObjectType: 'action',
                filter: {
                    _and: [
                        {
                            _not: {
                                status: 'Deleted'
                            }
                        }, {
                            objectType: 'case'
                        }, {
                            objectId: $scope.caseId
                        }
                    ]
                },
                sort: ['-startDate'],
                pageSize: 100,
                guard: function(updates) {
                    return _.find(updates, function(item) {
                        return (item.base.object.objectType === 'case') && (item.base.object.objectId === $scope.caseId);
                    }) !== undefined;
                }
            });
        }

        $scope.openAttachment = function(attachment) {
            // TODO fix me attachment.case_task is not defined
            $state.go('app.case.tasks-item', {
                caseId: $scope.caze.id,
                itemId: attachment.case_task.id
            });
        };

        $scope.getCaseTags = function(query) {
            return TagSrv.fromCases(query);
        };
    });

    angular.module('theHiveControllers').controller('CaseCustomFieldsCtrl', function($scope, $uibModal, CustomFieldsSrv) {

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
                var customFieldValue = {};
                customFieldValue[customField.type] = null;
                customFieldValue.order = _.max(_.pluck($scope.caze.customFields, 'order')) + 1;

                $scope.updateField('customFields.' + customField.reference, customFieldValue);
            });
        };

        $scope.updateCustomFieldsList = function() {
            CustomFieldsSrv.all().then(function(fields) {
                $scope.allCustomFields = _.omit(fields, _.pluck($scope.caze.customFields, 'name'));
                $scope.customFieldsAvailable = _.keys($scope.allCustomFields).length > 0;
            });
        };

        $scope.keys = function(obj) {
            return _.keys(obj);
        };

        $scope.updateCustomFieldsList();

        $scope.$on('case:refresh-custom-fields', function() {
            $scope.updateCustomFieldsList();
        });

        $scope.$watch('caze.customFields', function() {
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
