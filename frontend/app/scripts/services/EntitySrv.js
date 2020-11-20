(function() {
    'use strict';
    angular.module('theHiveServices').factory('EntitySrv', function($state) {
        var buildState = function(entity) {
            var state = {};
            if (entity._type === 'case') {
                state.name = 'app.case.details';
                state.params = {
                    caseId: entity.id
                };
            } else if (entity._type === 'Case') {
                state.name = 'app.case.details';
                state.params = {
                    caseId: entity._id
                };
            } else if (entity._type === 'case_artifact') {
                state.name = 'app.case.observables-item';
                state.params = {
                    caseId: entity.case.id,
                    itemId: entity.id
                };
            } else if (entity._type === 'case_artifact_job') {
                state.name = 'app.case.observables-item';
                state.params = {
                    caseId: entity.case_artifact.case.id,
                    itemId: entity.case_artifact.id
                };
            } else if (entity._type === 'case_task') {
                state.name = 'app.case.tasks-item';
                state.params = {
                    caseId: entity.case.id,
                    itemId: entity.id
                };
            } else if (entity._type === 'Task') {
                state.name = 'app.case.tasks-item';
                state.params = {
                    caseId: entity.extraData.case ? entity.extraData.case._id : entity.case._id,
                    itemId: entity._id
                };
            }

            return state;
        };

        var es = {
            'link': function(entity) {
                var state = buildState(entity);

                if (state.name) {
                    return $state.href(state.name, state.params);
                } else {
                    return 'unknown';
                }
            },
            'open': function(entity) {
                var state = buildState(entity);
                $state.go(state.name, state.params);
            }
        };
        return es;
    });
})();
