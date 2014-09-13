'use strict';

angular
  .module('moneyApp', [
    'ngResource',
    'ngRoute',
    'mgcrea.ngStrap',
    'fiestah.money'
  ])
  .config(function ($routeProvider) {
    $routeProvider
      .when('/', {
        loggedIn: '/debts',
        loggedOut: '/login'
      })
      .when('/login', {
        templateUrl: 'views/login.html',
        controller: 'AuthCtrl',
        publicAccess: true
      })
      .when('/register', {
        templateUrl: 'views/register.html',
        controller: 'AuthCtrl',
        publicAccess: true
      })
      .when('/expenses/view', {
        templateUrl: 'views/expenses/view.html',
        controller: 'ExpensesViewCtrl'
      })
      .when('/expenses/viewrec', {
        templateUrl: 'views/expenses/viewRec.html',
        controller: 'ExpensesViewRecCtrl'
      })
      .when('/expenses/add/:rec', {
        templateUrl: 'views/expenses/edit.html',
        controller: 'ExpensesEditCtrl'
      })
      .when('/expenses/edit/:id', {
        templateUrl: 'views/expenses/edit.html',
        controller: 'ExpensesEditCtrl'
      })
      .when('/reports/list', {
        templateUrl: 'views/reports/list.html',
        controller: 'ReportsListCtrl'
      })
      .when('/reports/generate', {
        templateUrl: 'views/reports/generate.html',
        controller: 'ReportsGenCtrl'
      })
      .when('/reports/view/:id', {
        templateUrl: 'views/reports/view.html',
        controller: 'ReportsViewCtrl'
      })
      .when('/admin/users', {
        templateUrl: 'views/admin/viewUsers.html',
        controller: 'AdminUsersCtrl'
      })
      .when('/debts', {
        templateUrl: 'views/debts.html',
        controller: 'DebtsCtrl'
      })
      .when('', {
        redirectTo: '/',
        publicAccess: true
      })
      .otherwise({
        redirectTo: '/',
        publicAccess: true
      });
  }).run(function ($rootScope, $location, userSrv, $route) {
    userSrv.checkActiveLogin();

    $rootScope.$on('$routeChangeStart', function (event, nextLoc, currentLoc) {
      if (nextLoc.$$route && nextLoc.loggedIn && nextLoc.loggedOut) {
        userSrv.checkActiveLogin(function () {
          $location.path(nextLoc.loggedIn);
        }, function () {
          $location.path(nextLoc.loggedOut);
        });
      } else {
        var closedToPublic = nextLoc.$$route && !nextLoc.$$route.publicAccess;
        if (closedToPublic) {
          userSrv.checkActiveLogin(null,
            function () {
              if (!userSrv.user.id) {
                $location.path('/login');
              }
            });
        }
      }
    });
  });
